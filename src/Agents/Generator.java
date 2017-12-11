package Agents;
import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Scanner;

import Execute.TeslaRun24;
import Utils.Methods;

public class Generator {
	//merit order matrix holding 9 technologies
	int nTech = 10; //number of technologies
	double[][] meritOrder = new double [nTech][4]; //matrix holding capacities and prices (first one blank)
	private double[] margPrices = new double [24];
	
	private double maxCap; //storage capacity in MWh
	private double maxP;//storage power constraint in MW
	private double effEle = 0.8; // efficiency of pump storage https://en.wikipedia.org/wiki/Dinorwig_Power_Station
	private double[] chargeProfile = new double[24];
	private double[] dischargeProfile = new double[24];
	private double[] storageProfile = new double[24];
	private double[][] storageCapacities = new double[4][36]; //matrix containing the data for renewable energy generation
	private double[][] storagePower = new double[4][36]; //matrix containing the data for renewable energy generation
	
	private double windGenData[][] = new double[365][24]; //annual wind generation data
	private double solarGenData[][] = new double[365][24]; //annual wind generation data
	private double RESGen[] = new double[24]; //daily RES generation profile
	private double windMultiplier, solarMultiplier; //wind scaling factor to simulate capacity increase
	private double[][] windCount = new double[4][36]; //Wind capacity scaling factor relative to 2015
	private double[][] solarCount = new double[4][36]; //Wind capacity scaling factor relative to 2015
	
	//TECHNOLOGY SPECIFICATION
	//technology type:    1=Biomass 2=GasCCS 3=CHP	 4=Gas	  5=Coal   6=Hydro   7=Marine  8=Nuclear   9=otherThermal(gas oil)	 10=Other renewables
	//those that participate in the BM 1, 2, 3, 4, 5, 8, 9
 	int[] technologyName = {1,       2,       3,      4,       5,       6,        7,        8,          9,        10};
	double[] efficiency  = {0.34,    0.5,     0.4,    0.5,    0.45,    0.45,     0.2,      0.32,       0.45,     1 };//fuel to electricity conversion efficiency
	double[] varCosts    = {2.3,     3.35,    2.30,   2.30,    2.09,    0.2,      0.2,      2.13,       0.88,     2.3}; //variable O&M costs (£/MWh)
	double[] CO2factor   = {0,       54,      500,    360,     910,     0,        0,        0,          610,      0}; // kg/MWh
	double[] rampingCost = {1.26,    0.35,    0.35,   0.35,    1.26,    0,        0,        80,          0.35,     0}; //RC values are taken from http://www.mech.kuleuven.be/tme/research/ based on 2015 EUR/GBP exchange rate
	//http://iea-etsap.org/E-TechDS/PDF/E14_CCS_oct2010_GS_gc_AD_gs.pdf - CCS (O%M +£30/MWh for gas), -8-12pp loss in efficiency compared to without CCS install, emissions: -85%
	//http://iea-etsap.org/E-TechDS/PDF/E04-CHP-GS-gct_ADfinal.pdf - CCGT + UKTM data
	//http://iea-etsap.org/E-TechDS/PDF/E03-Nuclear-Power-GS-AD-gct_FINAL.pdf
	//nuclear ramping: https://www.iaea.org/NuclearPower/Downloadable/Meetings/2014/2014-10-06-10-08-TM-NPE/4-Alexeeva-Economic_factors_Load_Following.pdf
	
	double[][] capacity = new double[36][nTech]; //capacity in MW
	double[][] fuelPrices = new double[36][nTech];//fuel prices in £/MWh 
	double[][] carbonPrices = new double[4][36]; // £/kg CO2
	double[][] imports = new double[4][36];
	double[][] totalGenCosts = new double[24][nTech];//total cost for generation by power plants
	double[][] bmPrices = new double[24][nTech];//total cost for generation by power plants
	double[][] powerGen = new double[24][nTech];//generation matrix for power plants
	double[][] bmGen = new double[24][nTech];//generation matrix for power plants
	private double[] WSuplifts = new double[365]; //COPs change during the year - come from calculations
	double[][] marginalPrices = new double[24][nTech];//generation matrix for power plants
	double[][] rescheduledGen = new double[24][nTech];//generation matrix for power plants
	double[] prevDayGen = new double[nTech];//generation matrix for power plants
	static double[] utilisedRES = new double[24];
	double[] curtailedRES = new double[24];
	double pCarbon, BMuplift = 1.07, errorRES = 0.08; //based on Ireland https://www.nrel.gov/docs/fy12osti/56130.pdf  default=0.08
	double trade;
	//http://www2.nationalgrid.com/UK/Industry-information/Electricity-system-operator-incentives/wind-generation-forecasting/
	//world forecasting error is around 3% https://www.iitk.ac.in/ime/anoops/for14/PPTs/Day%20-%202%20IITK/Wind%20Frorecasting-%20Balaraman.pdf
	private File file0, file1, file2; //file for tracking coordination and file for storing demand
	private double initEleStore; //
	private double uplift = 0.0005165; //0.0005457;// calibration against historical hourly data from APX

	//private double[] uplifts = new double[36];
		public Generator(int scenario){
		//READ DATA ON FUEL GENERATORS
		readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/FuelPrices"+scenario+".txt", fuelPrices, 36);//fuel prices get input manually (low, medium, high)
		readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/Capacities"+scenario+".txt", capacity, 36);
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/CarbonPrices.txt", this.carbonPrices, 1, 4, 36);//carbon prices exist for the 4 scenarios
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/Imports.txt", this.imports, 1, 4, 36);//carbon prices exist for the 4 scenarios
		//READ DATA ON RENEWABLES
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/systemWindGenPower.txt", this.windGenData, 1, 365, 24); // wind generation profile for year 2015 (NG data)
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/systemWindCount.txt", this.windCount, 1, 4, 36); //wind farm scaling factor for the four scenarios (FES data)
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/systemSolarGenPower.txt", this.solarGenData, 1, 365, 24); // wind generation profile for year 2015 (NG data)
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/systemSolarCount.txt", this.solarCount, 1, 4, 36); //wind farm scaling factor for the four scenarios (FES data)
		//READ DATA ON STORAGE
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/systemStorageCapacity.txt", this.storageCapacities, 1, 4, 36); //system level storage (FES)
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/systemStoragePower.txt", this.storagePower, 1, 4, 36); //system level storage (FES)
		//Methods.readDoubleData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/Uplifts.txt", this.uplifts, 36);
		Methods.readDoubleData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/WSuplifts.txt", WSuplifts, 365);
	}

	public void populateMeritOrder(int scenario, int year) {
		pCarbon = carbonPrices[scenario][year];
		for (int i=0; i<nTech; i++){
			meritOrder[i][0] = technologyName[i]; //column 1 = number of technology
			meritOrder[i][1] = capacity[year][i]; //column 2 is populated with installed capacity
			meritOrder[i][2] = varCosts[i] + fuelPrices[year][i]/efficiency[i] + CO2factor[i]*pCarbon; //column 4 is populated with prices
			meritOrder[i][3] = rampingCost[i];
		}
		for (int k = 0; k < meritOrder.length; k++) {
		    for (int l = 0; l < meritOrder[k].length; l++) {
		        System.out.print(meritOrder[k][l] + " ");
		    }
		    System.out.println();
		}
	}
	
	public void setScenarioParams(int scenario, int storage, int year) {
		this.maxCap = storageCapacities[scenario][year];
		this.maxP = storagePower[scenario][year];
		this.windMultiplier = windCount[scenario][year];
		this.solarMultiplier = solarCount[scenario][year];
		this.trade = imports[scenario][year];
		//this.uplift=uplifts[year];
		initEleStore = 0.5*maxCap;
	}
	
	public double[] calculateWholesalePrices(double[] load){
		double[] prices = new double[24];
		double[] totalCost = new double[24];
		for(int i=0; i<24; i++){
			ArrayList<Double> marginalPrices = new ArrayList<Double>();
			double marginalPrice=0;
			for(int j=0; j<nTech; j++){
				if(i==0){ //corresponds to hour 1
					if(TeslaRun24.day==TeslaRun24.startDay & TeslaRun24.year==TeslaRun24.startYear){
						totalGenCosts[i][j] = powerGen[i][j]*meritOrder[j][2];
						if(powerGen[i][j]>0){
							marginalPrice = meritOrder[j][2];
						}
					}else{
						double changeP = Math.abs(powerGen[i][j]-prevDayGen[j]);
						totalGenCosts[i][j] = powerGen[i][j]*meritOrder[j][2] + meritOrder[j][3]*Math.abs(changeP);
						if(powerGen[i][j]>0){
							marginalPrice = meritOrder[j][2] + meritOrder[j][3];
						}
					}
				}else{
					double changeP = Math.abs(powerGen[i][j]-powerGen[i-1][j]);
					totalGenCosts[i][j] = powerGen[i][j]*meritOrder[j][2] + meritOrder[j][3]*Math.abs(changeP);
					if(powerGen[i][j]>0){
						marginalPrice = meritOrder[j][2] + meritOrder[j][3];
					}
				}
				marginalPrices.add(marginalPrice);
				totalCost[i]+=totalGenCosts[i][j];
			}
			margPrices[i]=Collections.max(marginalPrices);
			prices[i]=totalCost[i]/load[i]+load[i]*uplift;
		}
		/*for(int i=0; i<24; i++){
			for(int j=0; j<nTech; j++){
		        System.out.print(totalGenCosts[i][j] + " ");
			}
		    System.out.println();
		}*/
		return prices;
	}
	
	public double[] reCalculateWholesalePrices(double[] load, double[] imbalance){
		double[] prices = new double[24];
		double[] totalCost = new double[24];
		for(int i=0; i<24; i++){
			ArrayList<Double> marginalPrices = new ArrayList<Double>();
			double marginalPrice=0;
			for(int j=0; j<nTech; j++){
				double rescheduledChangeP = Math.abs(rescheduledGen[i][j]-powerGen[i][j]);
				if(i==0){ //corresponds to hour 1
					if(TeslaRun24.day==TeslaRun24.startDay & TeslaRun24.year==TeslaRun24.startYear){
						totalGenCosts[i][j] = rescheduledGen[i][j]*meritOrder[j][2];
						if(rescheduledGen[i][j]>0){
							marginalPrice = Math.max(meritOrder[j][2],meritOrder[j][2]*BMuplift);
						}
					}else{
						double changeP = Math.abs(rescheduledGen[i][j]-prevDayGen[j]);
						totalGenCosts[i][j] = rescheduledGen[i][j]*meritOrder[j][2] + meritOrder[j][3]*Math.abs(changeP)+
								+ rescheduledChangeP*meritOrder[j][2]*BMuplift;
						if(rescheduledGen[i][j]>0){
							marginalPrice = Math.max(meritOrder[j][2],meritOrder[j][2]*BMuplift) + meritOrder[j][3];
						}
					}
				}else{
					double changeP = Math.abs(rescheduledGen[i][j]-rescheduledGen[i-1][j]);
					totalGenCosts[i][j] = rescheduledGen[i][j]*meritOrder[j][2] + meritOrder[j][3]*Math.abs(changeP)+
							+ rescheduledChangeP*meritOrder[j][2]*BMuplift;
					if(rescheduledGen[i][j]>0){
						marginalPrice = Math.max(meritOrder[j][2],meritOrder[j][2]*BMuplift) + meritOrder[j][3] ;
					}
				}
				bmGen[i][j]=rescheduledChangeP;
				bmPrices[i][j]=meritOrder[j][2];
				marginalPrices.add(marginalPrice);
				totalCost[i]+=totalGenCosts[i][j];
			}
			//totalCost[i]+=Math.abs(imbalance[i])*BMprice;
			margPrices[i]=Collections.max(marginalPrices)+load[i]*uplift;
			prices[i]=totalCost[i]/load[i]+load[i]*uplift;
		}
		/*for(int i=0; i<24; i++){
			for(int j=0; j<nTech; j++){
		        System.out.print(totalGenCosts[i][j] + " ");
			}
		    System.out.println();
		}*/
		return prices;
	}
	
	public double[] reCalculateWholesalePricesOld(double[] load) {
		double[][] prevMargPrices = marginalPrices.clone();
		double[] prices = new double[24];
		double[] totalCost = new double[24];
		double marginalPrice=0;
		for(int i=0; i<24; i++){
			ArrayList<Double> marginalPricesList = new ArrayList<Double>();
			for(int j=0; j<nTech; j++){
				double changeP = rescheduledGen[i][j]-powerGen[i][j];
				double prevCost = totalGenCosts[i][j];
				totalGenCosts[i][j] = prevCost + meritOrder[j][3]*Math.abs(changeP) + meritOrder[j][2]*changeP;
				if(rescheduledGen[i][j]>0){
					marginalPrice = prevMargPrices[i][j] + meritOrder[j][3];
				}
				marginalPricesList.add(marginalPrice);
				totalCost[i]+=totalGenCosts[i][j];
			}
			margPrices[i]=Collections.max(marginalPricesList) + load[i]*uplift;
			prices[i]=totalCost[i]/load[i] + load[i]*uplift;
		}
		for(int i=0; i<24; i++){
			for(int j=0; j<nTech; j++){
		        System.out.print(totalGenCosts[i][j] + " ");
			}
		    System.out.println();
		}
		return prices;
	}

	public void predictRES(int day) {
		for (int i=0; i<24; i++){
			RESGen[i] = windGenData[day][i] * windMultiplier + solarGenData[day][i] * solarMultiplier;
		}
	}
	
	public void scheduleGenerators(double[] load){
		try {
			IloCplex cplex = new IloCplex ();
			cplex.setOut(null);
			
			int maxT=24; //int nuclear=0;
			
			IloNumVar[][] powerGeneration = new IloNumVar[maxT][nTech];//power generated by each technology
			IloNumVar[] curtRES = new IloNumVar[maxT]; //curtailed RES
			IloNumVar[] chargePump = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargePump = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			
			IloNumExpr srmcCost, cycleCost; //cycling cost £/MWh
			IloNumExpr[][] totalCost = new IloNumExpr[maxT][nTech]; //total cost of generaton £/MWh
			IloNumExpr[] usedRES = new IloNumExpr[maxT]; //utilised RES
			IloNumExpr[] totalGen = new IloNumExpr[maxT]; //total power generated in each hour
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			
			int[] gensWithConstrainedCap = {0,1,2,3,4,5,6,7,9};
			
			for(int i=0; i<maxT; i++){
				for(int j : gensWithConstrainedCap){   
					powerGeneration[i][j] = cplex.numVar(0, meritOrder[j][1]); //power gen is bounded by capacity
				}
				powerGeneration[i][8]=cplex.numVar(0,Double.MAX_VALUE);
				curtRES[i]=cplex.numVar(0, RESGen[i]); //curtailed RES is bound by available RES
			}
			
			for (int i=0; i<24; i++){
				chargePump[i] = cplex.numVar(0, maxP); //charge P is bound by maxP
				dischargePump[i] = cplex.numVar(0, maxP);
			}
			
			//CYCLE COSTS
			for(int j=0; j<nTech; j++){
				srmcCost =cplex.prod(meritOrder[j][2],powerGeneration[0][j]);
				if(TeslaRun24.day==TeslaRun24.startDay & TeslaRun24.year==TeslaRun24.startYear){
					cycleCost=cplex.prod(meritOrder[j][3],  //cycling cost £/MWh
							cplex.abs(cplex.sum(powerGeneration[0][j],cplex.prod(-1,powerGeneration[0][j]))));// change in generation from previous hour
				}else{
					cycleCost = cplex.prod(meritOrder[j][3], cplex.abs(cplex.sum(powerGeneration[0][j],-prevDayGen[j])));
				}
				totalCost[0][j]=cplex.sum(srmcCost,cycleCost); //total cost = SRMCCost + cycling cost
			}
			
			for(int i=1; i<maxT; i++){
				for(int j=0; j<nTech; j++){
					srmcCost = cplex.prod(meritOrder[j][2],powerGeneration[i][j]);
					cycleCost = cplex.prod(meritOrder[j][3], cplex.abs(cplex.sum(powerGeneration[i][j],cplex.prod(-1,powerGeneration[i-1][j]))));
					totalCost[i][j]=cplex.sum(srmcCost,cycleCost); //total cost = SRMCCost + cycling cost
				}
			}
			
			//PUMP STORE
			for(int i=0; i<24; i++){
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargePump[i]), cplex.prod(-1,dischargePump[i])); //net storage for battery
			}
			
			availableEleEnergy[0] = cplex.sum(initEleStore, netEleEnergy[0]);
			
			for(int i=1; i<24; i++){
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[i-1],netEleEnergy[i]);
			}
			
			//RES utilisation
			for(int i=0; i<maxT; i++){
				usedRES[i]=cplex.sum(RESGen[i],cplex.prod(-1,curtRES[i]));//used RES = available RES - curt RES
			}
			
			//TOTAL GENERATION FROM DISPATCHABLE POWER PLANTS
			IloNumExpr[] totalGen1 = new IloNumExpr[maxT];
			
			for(int i=0; i<maxT; i++){
				totalGen[i]=powerGeneration[i][0]; //first populate by the 0th gen component for each hour 
				totalGen1[i]=totalGen[i];
				for(int j=1; j<nTech; j++){
					totalGen[i]=cplex.sum(totalGen1[i],powerGeneration[i][j]);
					totalGen1[i]=totalGen[i];
				}
			}
	
			IloNumExpr objective = cplex.sum(totalCost[0][0], totalCost[0][1]);
			IloNumExpr objective1 = objective;
			
			for(int j=2; j<nTech; j++){
				objective=cplex.sum(objective1, totalCost[0][j]);
				objective1=objective;
			}
			
			for(int i=1; i<maxT; i++){
				for(int j=0; j<nTech; j++){
					objective=cplex.sum(objective1, totalCost[i][j]);
					objective1=objective;
				}
			}
			
			//OBJECTIVE
			cplex.addMinimize(objective1);
			
			//CONSTRAINTS
			//1. generation must add up to demand
			for(int i=0; i<maxT; i++){
				cplex.addEq(cplex.sum(totalGen1[i],usedRES[i],dischargePump[i]), cplex.sum(cplex.sum(load[i], chargePump[i]),-trade)); //+trade means an import
			}
			
			/*for(int i=0; i<maxT-1; i++){
				cplex.addEq(powerGeneration[i][7], powerGeneration[i+1][7]);
			}*/
			
			for(int i=0; i<24; i++){
				cplex.addLe(availableEleEnergy[i], maxCap);
				cplex.addLe(0, availableEleEnergy[i]);
				cplex.add(cplex.ifThen(cplex.ge(chargePump[i],0.1), cplex.eq(dischargePump[i],0)));
				}
			
			//String file = new String("ModelLP.lp");
			
			//cplex.exportModel(file);
			
			if (cplex.solve()) {
				for(int i=0; i<maxT; i++){
					for(int j=0; j<nTech; j++){
						powerGen[i][j]=cplex.getValue(powerGeneration[i][j]);//extracting power generation matrix
					}
					utilisedRES[i]=cplex.getValue(usedRES[i]);
					curtailedRES[i]=cplex.getValue(curtRES[i]);
					chargeProfile[i]=cplex.getValue(chargePump[i]);
					dischargeProfile[i]=cplex.getValue(dischargePump[i]);
					storageProfile[i] = chargeProfile[i]-dischargeProfile[i];
				}
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				
				/*for(int i=0; i<maxT; i++){
					for(int j=0; j<nTech; j++){
				        System.out.print(powerGen[i][j] + " ");
					}System.out.print(utilisedRES[i] + " " + chargeProfile[i] + " " + dischargeProfile[i]);
				    System.out.println();
				}*/
			}else{
				System.out.println("Generator scheduling model not solved");
			}
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
			}
	}
	
	public void reScheduleGenerators(double[] load){
		try {
			IloCplex cplex = new IloCplex ();
			cplex.setOut(null);
			
			double[][] comitPowerGen = powerGen.clone(); //previous scheduling of power plants
			
			int maxT=24; //int nuclear=0;
			
			IloNumVar[][] powerGeneration = new IloNumVar[maxT][nTech];//power generated by each technology
			IloNumVar[] curtRES = new IloNumVar[maxT]; //curtailed RES
			IloNumVar[] chargePump = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargePump = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			
			IloNumExpr srmcCost, cycleCost; //cycling cost £/MWh
			IloNumExpr[][] totalCost = new IloNumExpr[maxT][nTech]; //total cost of generaton £/MWh
			IloNumExpr[] usedRES = new IloNumExpr[maxT]; //utilised RES
			IloNumExpr[] totalGen = new IloNumExpr[maxT]; //total power generated in each hour
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			
			int[] gensNotParticipating = {5, 6, 9};
			int[] gensParticipating = {0, 1, 2, 3, 4, 7}; //8 is unconstrained
			
			for(int i=0; i<maxT; i++){
				for(int j : gensParticipating){   
					powerGeneration[i][j] = cplex.numVar(0, meritOrder[j][1]); //power gen is bounded by capacity
				}
				for(int j : gensNotParticipating){   
					powerGeneration[i][j] = cplex.numVar(comitPowerGen[i][j], comitPowerGen[i][j]); //power gen is bounded by capacity
				}
				powerGeneration[i][8]=cplex.numVar(0,Double.MAX_VALUE);
				curtRES[i]=cplex.numVar(0, RESGen[i]); //curtailed RES is bound by available RES
			}
			
			for (int i=0; i<24; i++){
				chargePump[i] = cplex.numVar(0, maxP); //charge P is bound by maxP
				dischargePump[i] = cplex.numVar(0, maxP);
			}
			
			//CYCLE COSTS
			for(int j=0; j<nTech; j++){
				srmcCost =cplex.prod(meritOrder[j][2],powerGeneration[0][j]);
				if(TeslaRun24.day==TeslaRun24.startDay & TeslaRun24.year==TeslaRun24.startYear){
					cycleCost=cplex.prod(meritOrder[j][3],  //cycling cost £/MWh
							cplex.abs(cplex.sum(powerGeneration[0][j],cplex.prod(-1,powerGeneration[0][j]))));// change in generation from previous hour
				}else{
					cycleCost = cplex.prod(meritOrder[j][3], cplex.abs(cplex.sum(powerGeneration[0][j],-prevDayGen[j])));
				}
				totalCost[0][j]=cplex.sum(srmcCost,cycleCost); //total cost = SRMCCost + cycling cost
			}
			
			for(int i=1; i<maxT; i++){
				for(int j=0; j<nTech; j++){
					srmcCost = cplex.prod(meritOrder[j][2],powerGeneration[i][j]);
					cycleCost = cplex.prod(meritOrder[j][3], cplex.abs(cplex.sum(powerGeneration[i][j],cplex.prod(-1,powerGeneration[i-1][j]))));
					totalCost[i][j]=cplex.sum(srmcCost,cycleCost); //total cost = SRMCCost + cycling cost
				}
			}
			
			//PUMP STORE
			for(int i=0; i<24; i++){
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargePump[i]), cplex.prod(-1,dischargePump[i])); //net storage for battery
			}
			
			availableEleEnergy[0] = cplex.sum(initEleStore, netEleEnergy[0]);
			
			for(int i=1; i<24; i++){
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[i-1],netEleEnergy[i]);
			}
			
			//RES utilisation
			for(int i=0; i<maxT; i++){
				usedRES[i]=cplex.sum(RESGen[i],cplex.prod(-1,curtRES[i]));//used RES = available RES - curt RES
			}
			
			//TOTAL GENERATION FROM DISPATCHABLE POWER PLANTS
			IloNumExpr[] totalGen1 = new IloNumExpr[maxT];
			
			for(int i=0; i<maxT; i++){
				totalGen[i]=powerGeneration[i][0]; //first populate by the 0th gen component for each hour 
				totalGen1[i]=totalGen[i];
				for(int j=1; j<nTech; j++){
					totalGen[i]=cplex.sum(totalGen1[i],powerGeneration[i][j]);
					totalGen1[i]=totalGen[i];
				}
			}
	
			IloNumExpr objective = cplex.sum(totalCost[0][0], totalCost[0][1]);
			IloNumExpr objective1 = objective;
			
			for(int j=2; j<nTech; j++){
				objective=cplex.sum(objective1, totalCost[0][j]);
				objective1=objective;
			}
			
			for(int i=1; i<maxT; i++){
				for(int j=0; j<nTech; j++){
					objective=cplex.sum(objective1, totalCost[i][j]);
					objective1=objective;
				}
			}
			
			//OBJECTIVE
			cplex.addMinimize(objective1);
			
			//CONSTRAINTS
			//1. generation must add up to demand
			for(int i=0; i<maxT; i++){
				cplex.addEq(cplex.sum(totalGen1[i],usedRES[i],dischargePump[i]), cplex.sum(cplex.sum(load[i], chargePump[i]),-trade));
			}
			
			/*for(int i=0; i<maxT-1; i++){
				cplex.addEq(powerGeneration[i][7], powerGeneration[i+1][7]);
			}*/
			
			for(int i=0; i<24; i++){
				cplex.addLe(availableEleEnergy[i], maxCap);
				cplex.addLe(0, availableEleEnergy[i]);
				cplex.add(cplex.ifThen(cplex.ge(chargePump[i],0.1), cplex.eq(dischargePump[i],0)));
				}
			
			//String file = new String("ModelLP.lp");
			
			//cplex.exportModel(file);
			
			if (cplex.solve()) {
				for(int i=0; i<maxT; i++){
					for(int j=0; j<nTech; j++){
						rescheduledGen[i][j]=cplex.getValue(powerGeneration[i][j]);//extracting power generation matrix
					}
					utilisedRES[i]=cplex.getValue(usedRES[i]);
					curtailedRES[i]=cplex.getValue(curtRES[i]);
					chargeProfile[i]=cplex.getValue(chargePump[i]);
					dischargeProfile[i]=cplex.getValue(dischargePump[i]);
					storageProfile[i] = chargeProfile[i]-dischargeProfile[i];
				}
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				
				/*for(int i=0; i<maxT; i++){
					for(int j=0; j<nTech; j++){
				        System.out.print(powerGen[i][j] + " ");
					}System.out.print(utilisedRES[i] + " " + chargeProfile[i] + " " + dischargeProfile[i]);
				    System.out.println();
				}*/
			}else{
				System.out.println("Generator scheduling model not solved");
			}
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
			}
	}

	public void reScheduleGeneratorsOld(double[] load) {
		try {
			IloCplex cplex = new IloCplex ();
			cplex.setOut(null);
			
			double[][] costs =  totalGenCosts.clone(); //acquired costs from previous scheduling
			double[][] comitPowerGen = powerGen.clone(); //previous scheduling of power plants
			
			int maxT=24;
			
			IloNumVar[][] powerGeneration = new IloNumVar[maxT][nTech];//power generated by each technology
			IloNumVar[] curtRES = new IloNumVar[maxT]; //curtailed RES
			IloNumVar[] chargePump = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargePump = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			
			IloNumExpr[][] cycleCost = new IloNumExpr[maxT][nTech]; //cycling cost £/MWh
			IloNumExpr[][] genCost = new IloNumExpr[maxT][nTech]; //cycling cost £/MWh
			IloNumExpr[][] totalCost = new IloNumExpr[maxT][nTech]; //total cost of generaton £/MWh
			IloNumExpr[] usedRES = new IloNumExpr[maxT]; //utilised RES
			IloNumExpr[] totalGen = new IloNumExpr[maxT]; //total power generated in each hour
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			
			int[] gensWithConstrainedCap = {0,1,2,3,4,5,6,7,9};
			
			for(int i=0; i<maxT; i++){
				for(int j : gensWithConstrainedCap){   
					powerGeneration[i][j] = cplex.numVar(0, meritOrder[j][1]); //power gen is bounded by capacity
				}
				powerGeneration[i][8]=cplex.numVar(0,Double.MAX_VALUE);//oil generator has no constraint on capacity
				curtRES[i]=cplex.numVar(0, RESGen[i]); //curtailed RES is bound by available RES
			}
			
			for (int i=0; i<24; i++){
				chargePump[i] = cplex.numVar(0, maxP); //charge P is bound by maxP
				dischargePump[i] = cplex.numVar(0, maxP);
			}
			
			//CYCLE COSTS			
			for(int i=0; i<maxT; i++){
				for(int j=0; j<nTech; j++){
					//cost of increasing committed generation
					cycleCost[i][j] = cplex.prod(meritOrder[j][3],cplex.abs(cplex.sum(powerGeneration[i][j],-comitPowerGen[i][j])));
					//cost of generating additional power
					genCost[i][j] = cplex.prod(meritOrder[j][2], cplex.sum(powerGeneration[i][j],-comitPowerGen[i][j]));
					totalCost[i][j] = cplex.sum(costs[i][j], cplex.sum(cycleCost[i][j],genCost[i][j]));
				}
			}
			
			//PUMP STORE
			for(int i=0; i<24; i++){
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargePump[i]), cplex.prod(-1,dischargePump[i])); //net storage for battery
			}
			
			availableEleEnergy[0] = cplex.sum(initEleStore, netEleEnergy[0]);
			
			for(int i=1; i<24; i++){
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[i-1],netEleEnergy[i]);
			}
			
			//RES utilisation
			for(int i=0; i<maxT; i++){
				usedRES[i]=cplex.sum(RESGen[i],cplex.prod(-1,curtRES[i]));//used RES = available RES - curt RES
			}
			
			//TOTAL GENERATION FROM DISPATCHABLE POWER PLANTS
			IloNumExpr[] totalGen1 = new IloNumExpr[maxT];
			
			for(int i=0; i<maxT; i++){
				totalGen[i]=powerGeneration[i][0]; //first populate by the 0th gen component for each hour 
				totalGen1[i]=totalGen[i];
				for(int j=1; j<nTech; j++){
					totalGen[i]=cplex.sum(totalGen1[i],powerGeneration[i][j]);
					totalGen1[i]=totalGen[i];
				}
			}
	
			IloNumExpr objective = cplex.sum(totalCost[0][0], totalCost[0][1]);
			IloNumExpr objective1 = objective;
			
			for(int j=2; j<nTech; j++){
				objective=cplex.sum(objective1, totalCost[0][j]);
				objective1=objective;
			}
			
			for(int i=1; i<maxT; i++){
				for(int j=0; j<nTech; j++){
					objective=cplex.sum(objective1, totalCost[i][j]);
					objective1=objective;
				}
			}
			
			//OBJECTIVE
			cplex.addMinimize(objective1);
			
			//CONSTRAINTS
			//1. generation must add up to demand
			for(int i=0; i<maxT; i++){
				cplex.addEq(cplex.sum(totalGen1[i],usedRES[i],dischargePump[i]), cplex.sum(load[i], chargePump[i]));
				//cplex.addEq(powerGeneration[i][7],comitPowerGen[i][7]);
			}
			
			for(int i=0; i<24; i++){
				cplex.addLe(availableEleEnergy[i], maxCap);
				cplex.addLe(0, availableEleEnergy[i]);
				cplex.add(cplex.ifThen(cplex.ge(chargePump[i],0.001), cplex.eq(dischargePump[i],0)));
				}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			if (cplex.solve()) {
				for(int i=0; i<maxT; i++){
					for(int j=0; j<nTech; j++){
						rescheduledGen[i][j]=cplex.getValue(powerGeneration[i][j]);//extracting power generation matrix
					}
					utilisedRES[i]=cplex.getValue(usedRES[i]);
					curtailedRES[i]=cplex.getValue(curtRES[i]);
					chargeProfile[i]=cplex.getValue(chargePump[i]);
					dischargeProfile[i]=cplex.getValue(dischargePump[i]);
					storageProfile[i] = chargeProfile[i]-dischargeProfile[i];
				}
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				
				for(int i=0; i<maxT; i++){
					for(int j=0; j<nTech; j++){
				        System.out.print(rescheduledGen[i][j] + " ");
					}System.out.print(utilisedRES[i] + " " + chargeProfile[i] + " " + dischargeProfile[i]);
				    System.out.println();
				}
			}else{
				System.out.println("Generator rescheduling model not solved");
			}
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
			}
	}
	
	public void readData(String doc, double[][] array, int l) {
		try(Scanner file = new Scanner(new FileReader(doc)))
		{
			for(int i=0; i<l; i++){
				for(int j=0; j<nTech; j++){
					array[i][j]=file.nextDouble();							
				}
			}
		}
		catch (Exception e) {
		    System.err.println("Exception present" + e);
		}//System.out.println(Arrays.toString(array));
	}

	public void createFile(int scenario) {
		file0 = new File(TeslaRun24.dir+"generation_data.txt");
		file1 = new File(TeslaRun24.dir+"bm_volumes.txt");
		file2 = new File(TeslaRun24.dir+"bm_prices.txt");
		Methods.writeToFile(file0, "scenario,year,day,storage,hour,biomass,CCS,CHP,gas,coal,hydro,marine,nuclear,oil,otherRES,wind&solar, curtRES,chargePump, dischargePump,import");
		Methods.writeToFile(file1, "scenario,year,day,storage,hour,biomass,CCS,CHP,gas,coal,hydro,marine,nuclear,oil,otherRES");
		Methods.writeToFile(file2, "scenario,year,day,storage,hour,biomass,CCS,CHP,gas,coal,hydro,marine,nuclear,oil,otherRES");
		
	}

	public void writeGenData(int scenario, int year, int day, int storage) throws IOException {
		for(int j=0; j<nTech; j++){
			prevDayGen[j]=rescheduledGen[23][j];
		}
		Methods.writeToFile(file0, scenario + "," + year + ","+day+","+storage+",", rescheduledGen, utilisedRES,curtailedRES,chargeProfile,dischargeProfile,trade);
		Methods.writeToFile(file1, scenario + "," + year + ","+day+","+storage+",", bmGen);
		Methods.writeToFile(file2, scenario + "," + year + ","+day+","+storage+",", bmPrices);
	}

	public double[] getMargPrices() {
		return margPrices;
	}

	public void setMargPrices(double[] margPrices){
		this.margPrices = margPrices;
	}

	public static double[] getUsedRES() {
		return utilisedRES;
	}

	public void readActualRES(double[] imbalance) {
		Methods.zero(imbalance);
		double[] predictedRES = RESGen.clone();
		double predictError = 0;
		Random r = new Random();
		for(int i=0; i<24; i++){
			predictError = r.nextGaussian()*errorRES*predictedRES[i];
			RESGen[i] += predictError;
			imbalance[i] += predictError;
		}
	}

	public void initialise(int day) {
		uplift = WSuplifts[day];
	}
}

