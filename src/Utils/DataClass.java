package Utils;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class DataClass {
	//CONSUMER DATA
	public String[] conNames = {"domestic", "commercial", "industrial", "transport"};
	
	public double[][][] conCount = new double[4][36][4]; //N scenarios, N years, N consumers
	public double[][][] hpCount = new double[4][36][4]; //N scenarios, N years, N consumers
	public double[][][] rhCount = new double[4][36][4]; //N scenarios, N years, N consumers
	public double[][][] pvCount = new double[4][36][4]; //N scenarios, N years, N consumers
	public double[][][] tesCount = new double[4][36][4]; //N scenarios, N years, N consumers
	public double[][][] esCount = new double[4][36][4]; //N scenarios, N years, N consumers
	public int[][][] evCount = new int[4][36][4]; //N scenarios, N years, N consumers
	public static double[][] correctionFactors = new double[4][6];
	
	public static double[][][] demandDataBL = new double[365][24][3];
	public static double[][][] demandDataHP = new double[365][24][3];
	public static double[][][] demandDataRH = new double[365][24][3];
	public static double[][][] solarGenData = new double[365][24][3];
	public static double[][] transportChargeData = new double[365][24];
	public static double[][] transportDischargeData = new double[365][24];
	
	public static double[] HPpowerRatings = {2.1, 1.4, 9.0, 0};// determine as the maximum power demand for a thermal electricity profile
	public static double[] RHpowerRatings = {6.0, 4.3, 27.2, 0};// determine as the maximum power demand for a thermal electricity profile
	public static double[] EScapacities = {6.0, 9, 65, 2.9};
	public static double[] TEScapacities = {4.7, 7.4, 51.1, 0};
	public static int[][] EVchargeTimes = new int[][]{
		//start and finish for EV charging 
		{17, 7},  //domestic type
		{7, 17},  //commercial type 
		{7, 17},  //industrial type
						};
	
	//SYSTEM DATA
	public static double[] COPs = new double[365]; //COPs change during the year - come from calculations
	public static double[][] nConMultipliers = new double[4][10]; //4 sectors and 10 consumer types
	private double[][] techNumbers = new double[conNames.length][6];
	
	public DataClass(){
		Methods.readDoubleData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/COPdata.txt", COPs, 365);
		for(int i=0; i<conNames.length; i++){
			String sector = conNames[i];
			//BASELOAD PROFILES
			Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/"+ sector + "ConsumerCount.txt", conCount, i, 4, 36); 
			if(sector!="transport"){
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/" + sector + "DemandProfile.txt", demandDataBL, i, 365, 24);
				//HP DATA
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/"+ sector + "HPCount.txt", hpCount, i, 4, 36); 
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/" + sector + "HPDemandProfile.txt", demandDataHP, i, 365, 24);
				//RH DATA
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/"+ sector + "RHCount.txt", rhCount, i, 4, 36); 
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/" + sector + "RHDemandProfile.txt", demandDataRH, i, 365, 24);
				//SOLAR DATA
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/"+ sector + "SolarCount.txt", pvCount, i, 4, 36); 
				//TES DATA
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/"+ sector + "TESCount.txt", tesCount, i, 4, 36);
				//Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/"+ sector + "EVcount.txt", evCount, i, 4, 36);
				//SOLAR DATA
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/"+ sector + "SolarGen.txt", solarGenData, i, 365, 24); 
			}
			//ES DATA
			Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/"+ sector + "ESCount.txt", esCount, i, 4, 36);
			if(sector=="transport"){
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/" + sector + "ChargeProfile.txt", transportChargeData, 1, 365, 24); //individual non-thermal annual electricity demand profile (i.e. non-deferrable)
				Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/" + sector + "DischargeProfile.txt", transportDischargeData, 1, 365, 24); //individual non-thermal annual electricity demand profile (i.e. non-deferrable)
			}
		}
	}
	
	public void setTechValues(int scenario, int storage, int year){
		for(int i=0; i<conNames.length; i++){
			double N = conCount[scenario][year][i]; //total number of consumers
			techNumbers[i][0] = N;
			techNumbers[i][1] = Math.min(hpCount[scenario][year][i], N);
			techNumbers[i][2] = Math.min(rhCount[scenario][year][i], N);
			techNumbers[i][3] = Math.min(pvCount[scenario][year][i], N);
			techNumbers[i][4] = Math.min(tesCount[storage][year][i], N);
			if(i==3){ //corresponds to transport consumer
				techNumbers[i][5] = Math.min(esCount[scenario][year][i], N);
			}else{
				techNumbers[i][5] = Math.min(esCount[storage][year][i], N);
			}
			//techNumbers[i][6] = Math.min(evCount[scenario][year][i], N);
		}
	}
	
	public void allocateConsumers(){
		try {
			
			IloCplex cplex = new IloCplex ();
			cplex.setOut(null);
			
			//CONSUMER MULTIPLIERS I AM TRYING TO FIND
			int statSectors = conNames.length-1;
			IloNumVar[][] conMultipliers = new IloNumVar[statSectors][10]; //3 stationery sectors and 10 consumer types
			
			for (int i=0; i<statSectors; i++){ //i is for consumer sectors
				for(int j=0; j<6; j++){
					//the multiplier number is constrained by the number of total consumers
					conMultipliers[i][j] = cplex.numVar(1, techNumbers[i][0]); 
				}
				for(int j=5; j<10; j++){
					//the multiplier number is constrained by the number of total consumers
					conMultipliers[i][j] = cplex.numVar(1, techNumbers[i][0]); 
				}
			}
			
			//DEFINITIONS
			IloNumExpr[][] totNumbers = new IloNumExpr[statSectors][6]; //total technology numbers
		
			//calculate net charged energy in electrical and thermal storage
			for(int i=0; i<statSectors; i++){
				//total number of modelled consumers
				//for(int j=1; j<11; j++){
					totNumbers[i][0] = cplex.sum(cplex.sum(cplex.sum(cplex.sum(cplex.sum(cplex.sum(cplex.sum(cplex.sum(cplex.sum(
							conMultipliers[i][0],conMultipliers[i][1]),conMultipliers[i][2]),conMultipliers[i][3]),conMultipliers[i][4]),
					conMultipliers[i][5]),conMultipliers[i][6]),conMultipliers[i][7]),conMultipliers[i][8]),conMultipliers[i][9]);
				//}
				//total number of HP
				totNumbers[i][1]=cplex.sum(cplex.sum(conMultipliers[i][1],conMultipliers[i][2]),conMultipliers[i][8]);
				//total number of RH
				totNumbers[i][2]=cplex.sum(cplex.sum(conMultipliers[i][3],conMultipliers[i][4]),conMultipliers[i][9]);
				//total number of PV
				totNumbers[i][3]=cplex.sum(cplex.sum(cplex.sum(conMultipliers[i][5],conMultipliers[i][6]),conMultipliers[i][8]),conMultipliers[i][9]) ;
				//total number of TES
				totNumbers[i][4]=cplex.sum(cplex.sum(cplex.sum(conMultipliers[i][2],conMultipliers[i][4]),conMultipliers[i][8]),conMultipliers[i][9]);
				//total number of ES
				totNumbers[i][5]=cplex.sum(cplex.sum(cplex.sum(conMultipliers[i][6],conMultipliers[i][7]),conMultipliers[i][8]),conMultipliers[i][9]);
				//total number of EV
				//totNumbers[i][6]= conMultipliers[i][10];
			}
			
			//TRY TO MATCH VALUES OF MULTIPLIERS TO THE ACTUAL DATA
			IloNumExpr objective = cplex.prod(cplex.sum(totNumbers[0][0], -techNumbers[0][0]), cplex.sum(totNumbers[0][0], -techNumbers[0][0]));
			for(int j=1; j<6; j++){
				IloNumExpr objective1 = objective;
				IloNumExpr x1 = cplex.prod(cplex.sum(totNumbers[0][j], -techNumbers[0][j]), cplex.sum(totNumbers[0][j], -techNumbers[0][j]));
				objective = cplex.sum(objective1, x1);
			}
			
			for(int i=1; i<3; i++){
				for( int j=0; j<6; j++){
					IloNumExpr objective1 = objective;
					IloNumExpr x1 = cplex.prod(cplex.sum(totNumbers[i][j], -techNumbers[i][j]), cplex.sum(totNumbers[i][j], -techNumbers[i][j]));
					objective = cplex.sum(objective1, x1);
				}
			}
			
			//define objective
			cplex.addMinimize(objective);
			
			//CONSTRAINTS
			/*for(int i=0; i<statSectors; i++){
				//cplex.addEq(totNumbers[i][0], techNumbers[i][0]); //total consumers
				cplex.addEq(totNumbers[i][1], techNumbers[i][1]); //HP
				cplex.addEq(totNumbers[i][2], techNumbers[i][2]); //RH
				cplex.addEq(totNumbers[i][3], techNumbers[i][3]); //PV
				//cplex.addEq(totNumbers[i][4], techNumbers[i][4]); //TES
				cplex.addEq(totNumbers[i][5], techNumbers[i][5]); //ES
			}*/
			
			//cplex.addEq(totNumbers[2][0], techNumbers[2][0]); //total consumers
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int i=0; i<statSectors; i++){
					for(int j=0; j<10; j++){
						nConMultipliers[i][j]= (int) cplex.getValue(conMultipliers[i][j]);
					}
				}
				nConMultipliers[3][7]=techNumbers[3][5];
				for(int i=0; i<statSectors; i++){
					correctionFactors[i][0]=1.0*(nConMultipliers[i][0]+nConMultipliers[i][1]+nConMultipliers[i][2]+
							+nConMultipliers[i][3]+nConMultipliers[i][4]+nConMultipliers[i][5]+nConMultipliers[i][6]+nConMultipliers[i][7]+
							+nConMultipliers[i][8]+nConMultipliers[i][9])/(1.0 * techNumbers[i][0]);
					correctionFactors[i][1]=1.0*(nConMultipliers[i][1]+nConMultipliers[i][2]+nConMultipliers[i][8])/(1.0*techNumbers[i][1]);
					correctionFactors[i][2]=1.0*(nConMultipliers[i][3]+nConMultipliers[i][4]+nConMultipliers[i][9])/(1.0*techNumbers[i][2]);
					correctionFactors[i][3]=1.0*(nConMultipliers[i][5]+nConMultipliers[i][6]+nConMultipliers[i][8]+nConMultipliers[i][9])/(1.0*techNumbers[i][3]);
					correctionFactors[i][4]=1.0*(nConMultipliers[i][2]+nConMultipliers[i][4]+nConMultipliers[i][8]+nConMultipliers[i][9])/(1.0*techNumbers[i][4]);
					if(techNumbers[i][5]>0){
					correctionFactors[i][5]=1.0*(nConMultipliers[i][6]+nConMultipliers[i][7]+nConMultipliers[i][8]+nConMultipliers[i][9])/(1.0*techNumbers[i][5]);
					}
				}
				correctionFactors[3][0]=1;
				correctionFactors[3][5]=1;
			}else{
				System.out.println("Allocation model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}
}
