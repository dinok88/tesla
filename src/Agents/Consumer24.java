package Agents;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import Execute.TeslaRun24;
import Utils.DataClass;
import Utils.Methods;

public class Consumer24 {
	//Consumer specific variables
	//protected double[][] consumerCount = new double[4][36]; //matrix of consumer population for different scenarios
	private String name, sector; //name of consumer type (Sector)
	private int type, subType;
	private double conMultiplier; //number of modelled consumers for a particular agent
	private double demand[] = new double[24];//total consumer demand for electricity
	private double demandPrevious[] = new double[24];//total consumer demand for electricity
	private double flexDemand[] = new double[24]; //flexible demand profile for 
	private double demandProfileBL[] = new double[24]; //baseload demand profile for the day
	private double thermalDemand[] = new double[24]; //electricity demand by a thermal source
	private double demandProfileTherm[] = new double[24]; //heat demand by consumer
	
	//daily renewable generation profiles
	private double solarGen[] = new double[24]; //daily solar generation profile
	private double curtRES[] = new double[24];
	private double usedRES[] = new double[24];
	private double residual[] = new double[24];
	private double residualPrevious[] = new double[24];
	private double solarMultiplier;
	
	//Electric store (ES) variables
	private double chargeProfile[] = new double[24]; //charge profile for ES
	private double dischargeProfile[] = new double[24]; //discharge profile for ES
	private double storageProfile[] = new double[24];
	private double maxCap=0, EScap; //storage capacity for electric store (aggregated across 1000 consumers [MWh])
	private double maxP=0;//charge power for electric store (aggregated across1000 consumers [MW])
	private double effEle = 0.8; //efficiency of electric store
	private double initEleStore; //initial energy stored in ES

	//Electric vehicle (EV) variables
	private double demandEV[] = new double[24]; //electricity demand by an EV for the sector
	private double evPower = 3, evCap = 24; //individual EV power [MW] and capacity [MWh] (aggregated to 1000 consumers) - based on Nissan Leaf and slow charging
	private double SOC1, SOC2; //initial and final EV battery state-of-charge (SOC)
	private int EVstart=17, EVfinish=7; //time of EV plug-in and plug-out (5pm and 7am)
	private double maxEVpower=0, maxEVcap=0; //consumer EV power [MW] and capacity [MWh]
	private double effEV=0.8; //EV battery efficiency
	
	//Heat pump (HP) - thermal energy store (TES) system variables
	private double maxCapTherm=0, TEScap; //Energy capacity of TES [MWh]
	private double powerEH, maxPowerEH;//individual HP power rating for different sector (approx 3.4kW per one)
	private double COP = 2.5; //coefficient of performance for a heat pump will change throughout the year
	private double effTherm = 0.98; //efficiency of thermal energy store
	private double initHeatStore; //initial energy stored in thermal store
	private double ehMultiplier;
	private double maxThermP;
	
	//Other variables
	double errorDemand = 0.005; //from calibration
	boolean HP=false, RH=false, PV=false, TES=false, ES=false, EV=false;
	private int sup=0; //supplier identifier
	public boolean DSMrespond=false;
	private double[] signal = new double[24];
	protected int startT=0; //start of optimisation hour
	private File file1; //file to store demand information
	
	public Consumer24(int s, int k, int n, String sector, int startT, int conType, int conSubtype, int[][] techMatrix, boolean respondRate){ //multiplier = scaling factor to represent total demand across all consumers
		//HP,RH,PV,TES,ES,EV
		int HP = techMatrix[conSubtype][0];
		int RH = techMatrix[conSubtype][1];
		int PV = techMatrix[conSubtype][2]; 
		int TES = techMatrix[conSubtype][3]; 
		int ES = techMatrix[conSubtype][4];
		int EV = techMatrix[conSubtype][5];
		this.sector=TeslaRun24.conNames[conType];
		
		this.type = conType;
		this.subType = conSubtype;
		this.sup=s;
		
		DSMrespond=respondRate;
		this.setName(sector+HP+RH+PV+TES+ES+EV+"_"+k+"DSM"+respondRate); //consumer name
		
		if(HP==1){
			this.powerEH=DataClass.HPpowerRatings[conType];//setting the nominal HP power rating per 000's consumers [MW]
			this.HP=true;
		}
		
		if(RH==1){
			this.powerEH=DataClass.RHpowerRatings[conType];//setting the nominal HP power rating per 000's consumers [MW]
			this.RH=true;
		}
		
		if(PV==1){
			this.PV=true;
		}
		if(TES==1){
			this.TEScap = DataClass.TEScapacities[conType];
			this.TES=true;
		}
		if(ES==1){
			this.EScap=DataClass.EScapacities[conType];
			this.ES=true;
		}
		if(EV==1){
			this.EV=true;
			this.EVstart=DataClass.EVchargeTimes[conType][0];
			this.EVfinish=DataClass.EVchargeTimes[conType][1];
		}
	}
	
	//METHODS	
	public void setScenarioParams(int scenario, int store, int year, double[][] nConMultipliers, int[][] nConTypes, double[][] correct){//to adjust capacities and power divide by the conMultiplier	
		//number of actual consumers of this type (1000s)
		double n0 = correct[type][0];
		conMultiplier = nConMultipliers[type][subType] / nConTypes[type][subType];
		
		if(this.HP==true){
			double n1=correct[type][1];
			maxPowerEH = powerEH * conMultiplier / n1;
			ehMultiplier = conMultiplier / n1;
		}
		
		if(this.RH==true){
			double n2=correct[type][2];
			this.maxPowerEH = this.conMultiplier * powerEH / n2; //charging power is determined based on the powerWall capacity/power ratio (50% responsive)
			ehMultiplier = conMultiplier / n2;
			COP=1;
		}
		
		if(this.PV==true){
			double n3=correct[type][3];
			this.solarMultiplier = this.conMultiplier / n3; //number of solar PV farms per modelled consumer
		}
		
		if(this.TES==true){
			double n4=correct[type][4];
			maxCapTherm = TEScap * conMultiplier / n4;
			maxThermP=maxCapTherm/2; //set thermal storage capacity
			initHeatStore = 0.5*maxCapTherm; //initial storage is 50% of total capacity
		}
		if(this.ES==true){
			double n5=correct[type][5];
			maxCap = EScap * conMultiplier / n5;
			if(type==3){
				maxP = 0.5 * conMultiplier / n5; //for transport power is 0.5MW per thousands fleet
			}else{
				maxP = maxCap/2;//set electrical storage capacity
			}
			initEleStore = 0.5*maxCap; //initial storage is 50% of total capacity
		}
		conMultiplier = conMultiplier/n0;
		//this.maxEVpower=evPower*evCount[scenario][year];
	}
	
	public void initialise(int day){//s=storage scenario
		//file1 = new File(name + "demand, scenario = " + scenario + "storage = " + storage + ".txt");
		if(EV==true){
			//for future EV operation with different sectors
			Random r = new Random(); 
			this.SOC1=(int) (r.nextGaussian() * 0.1 + 0.67);
			this.SOC2=0.9;
			scheduleEV();
		}
		if(HP==true){
			this.COP=DataClass.COPs[day];
			//this.COP=(this.rhMultiplier+this.hpMultiplier*TeslaRun24.COPs[day])/(this.rhMultiplier+this.hpMultiplier);
		}
		predictDemand(day);
		setResidual();
	}
	
	public void readActualDemand(int day){//s=storage scenario
		predictDemand(day);
		setResidual();
	}

	private void scheduleEV() {
		double energy = (SOC2-SOC1)*maxEVcap;
		for(int i=EVstart; i<24+EVfinish; i++){
			int j=i%24;
			double charge = Math.min(energy, maxEVpower);
			demandEV[j]=charge;
			energy-=charge;
		}
	}

	public void predictDemand(int day) {
		for (int i=0; i<24; i++){
			if(this.sector.equals("transport")){
				chargeProfile[i]=DataClass.transportChargeData[day][i] * conMultiplier;
				dischargeProfile[i]=DataClass.transportDischargeData[day][i] * conMultiplier;
				demand[i]=chargeProfile[i];
			}else{
				demandProfileBL[i]=DataClass.demandDataBL[day][i][type] * conMultiplier;//individual consumer demand is multiplied by the number of consumers
				if(this.HP==true){
					thermalDemand[i]=DataClass.demandDataHP[day][i][type] * ehMultiplier;//individual HP demand is multiplied by the number of consumers with HPs
				}
				if(this.RH==true){
					thermalDemand[i]=DataClass.demandDataRH[day][i][type] * ehMultiplier;//individual HP demand is multiplied by the number of consumers with HPs
				}
				flexDemand[i]=thermalDemand[i]+storageProfile[i]+demandEV[i]; //flexible demand is the sum of demand by HP and storage 
				demand[i]=demandProfileBL[i]+flexDemand[i]; //domDemand gets updated
				demandProfileTherm[i]=thermalDemand[i]*COP; //demand by HP translated into thermal demand by consumer = heat out of HP * efficiency going into TES * efficiency going out of TES
				if(this.PV==true && solarMultiplier > 0){
					solarGen[i]=DataClass.solarGenData[day][i][type] * solarMultiplier; //installed solar PV capacity is updated
				//System.out.println(DataClass.solarGenData[day][i][type]);
				}
			}
		}
	}
	
	void setResidual() {
		for(int i=0; i<24; i++){
			usedRES[i]=Math.min(demand[i], solarGen[i]);
			residual[i]=demand[i]-usedRES[i];
			curtRES[i]=solarGen[i]-usedRES[i];
		}
	}

	public double[] getResidual() {
		return residual;
	}
	
	public double[] getCurtRES() {
		return curtRES;
	}

	public void updateDemand() {
		for(int i=0; i<24; i++){
			flexDemand[i] = thermalDemand[i] + storageProfile[i]+demandEV[i];
			demand[i]=flexDemand[i]+demandProfileBL[i];
		}
		//System.out.println(Arrays.toString(demand));
	}
	
	public void optimiseDF() {
		//Methods.writeToFile(file1, "residual before coordination", residual);
		double averageRes, totRes=residual[0];
		
		for(int i=1; i<24; i++){
			totRes+=residual[i];
		}
		averageRes = totRes / 24.0;
		//signal=getResidual().clone();
		Methods.optimiseDemandDFDistrib(
				maxCapTherm, maxCap, //consumer flattens 
	    		maxP, maxThermP,//electrical storage power capacity
	    		maxPowerEH, //nominal capacity for the heat pump 
	    		effEle, COP, effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
	    		signal, //optimisation signal sent to consumers 
	    		chargeProfile, dischargeProfile, storageProfile, //charging and discharging profile for the battery
	    		thermalDemand,//charging and discharging profile for the battery
	    		demandProfileTherm, demandProfileBL,
	    		solarGen, averageRes,
	    		initEleStore, initHeatStore, startT,
	    		SOC1, SOC2, demandEV, evCap,
				maxEVpower, EVstart, EVfinish, effEV, sector);
		updateDemand();
		setResidual();
	}

	public void optimiseOnPrice(double[] prices) {
		signal=prices.clone();
		Methods.optimiseDemandOnPriceDistrib(
				maxCapTherm, maxCap, //consumer flattens 
	    		maxP, maxThermP,//electrical storage power capacity
	    		maxPowerEH, //nominal capacity for the heat pump 
	    		effEle, COP, effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
	    		signal, //optimisation signal sent to consumers 
	    		chargeProfile, dischargeProfile, storageProfile, //charging and discharging profile for the battery
	    		thermalDemand,
	    		demandProfileTherm, demandProfileBL,
	    		solarGen,
	    		initEleStore, initHeatStore, startT,
	    		SOC1, SOC2, demandEV, evCap,
				maxEVpower, EVstart, EVfinish, effEV, sector);
		updateDemand();
		setResidual();
	}

	public void scheduleDFCentral(double[] signal, double LR){
		double[] demand0=residual.clone(); //assign previous flexible demand to control deviation
		//double[] flexDemand0=flexDemand.clone(); //assign previous flexible demand to control deviation
		Methods.optimiseDemandDFCentral(
				maxCapTherm, maxCap, //consumer flattens 
	    		maxP, maxThermP,//electrical storage power capacity
	    		maxPowerEH, //nominal capacity for the heat pump 
	    		effEle, COP, effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
	    		signal, //optimisation signal sent to consumers 
	    		chargeProfile, dischargeProfile, storageProfile, //charging and discharging profile for the battery
	    		thermalDemand,//charging and discharging profile for the battery
	    		demandProfileTherm, demandProfileBL, demand0,
	    		solarGen,
	    		initEleStore, initHeatStore, LR, startT,
	    		SOC1, SOC2, demandEV, evCap,
				maxEVpower, EVstart, EVfinish, effEV, sector);
	}
	
	public void scheduleOnPriceCentral(double[] signal, double LR){
		//double[] flexDemand0=flexDemand.clone(); //assign previous flexible demand to control deviation
		residualPrevious=residual.clone();
		demandPrevious=demand.clone();

		Methods.optimiseDemandDFCentral(
				maxCapTherm, maxCap, //energy capacity of heat and electrical storage
				maxP, maxThermP, //electrical and thermal power constraints
				maxPowerEH, //nominal capacity for the heat pump 
				effEle, COP, effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
				signal, //optimisation signal sent to consumers 
				chargeProfile, dischargeProfile, storageProfile, //charging and discharging profile for the battery
				thermalDemand,//charging and discharging profile for the battery
				demandProfileTherm, demandProfileBL, residualPrevious,
				solarGen,
	    		initEleStore, initHeatStore, LR, startT,
				SOC1, SOC2, demandEV, evCap,
				maxEVpower, EVstart, EVfinish, effEV, sector);
	}

	public double[] getDemand() {
		return demand;
	}

	public void writeResidual(int scenario, int year, int day, int storage) throws IOException{
		Methods.writeToFile(file1, scenario + "," + year + ","+day+","+storage+","+sup+",", residual, demand, usedRES, curtRES);	
	}

	public double getMaxCap() {
		return maxCap;
	}

	public void setMaxCap(double maxCap) {
		this.maxCap = maxCap;
	}

	public double getConMultiplier() {
		return conMultiplier;
	}

	public void setConMultiplier(double conMultiplier) {
		this.conMultiplier = conMultiplier;
	}

	public void writeNewLine() {
		Methods.addNextLine(file1);
	}

	public void createFile(int scenario){	
		  file1 = new File(TeslaRun24.dir+getName() + "_aggregator"+this.sup+"_DR" + DSMrespond + ".txt");
		  //create header
		  Methods.writeToFile(file1, "scenario,year,day,storage,supplier,hour,residual,demand, usedRES, curtRES");
	}

	public double getMaxCapTherm() {
		return maxCapTherm;
	}

	public void setMaxCapTherm(double maxCapTherm) {
		this.maxCapTherm = maxCapTherm;
	}

	public String getName() {
		return name;
	}

	public void setName(String name1) {
		this.name = name1;
	}

	public void setResidual(double residual[]) {
		this.residual = residual;
	}

	public double[] getResidualPrevious() {
		return residualPrevious;
	}

	public void setResidualPrevious(double residual1[]) {
		this.residualPrevious = residual1;
	}

	public double[] getDemandPrevious() {
		return demandPrevious;
	}

	public void setDemandPrevious(double demand1[]) {
		this.demandPrevious = demand1;
	}

	public void setDemand(double demand[]) {
		this.demand = demand;
	}

	public int getSup() {
		return sup;
	}

	public double[] getUsedRES() {
		return usedRES;
	}

	public void chooseSupplier(double[][] tariffs) {
		double price = tariffs[1][sup];
		for(int i=0; i<tariffs.length; i++){
			if(tariffs[1][i]<price){
				this.sup = (int) tariffs[0][i];
			}
		}	
	}

	public double[] getThermalDemand() {
		return this.thermalDemand;
	}

	public double getSolarMultiplier() {
		return solarMultiplier;
	}


}