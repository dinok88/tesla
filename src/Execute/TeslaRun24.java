package Execute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import Utils.DataClass;
import Agents.Aggregator24;
import Agents.Consumer24;
import Agents.Generator;
import Agents.SystemOperator;

public class TeslaRun24 {
	private static int simLength = 36;//length of simulation in years (36 is the default max)
	public static int startYear = 0;//0 setting corresponds to 2015
	public static int startDay = 0;//0 setting corresponds to 2015
	public static int scenario, year, storage, day;
	public static String[] conNames = {"domestic", "commercial", "industrial", "transport"};
	public static double[] DSMresponseRates = {1};//,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0}; //response rate to signalling by consumers
	public static double DSMrespondRate;
	static String[] scenarios = {"BAU","TDCP","SP","CP"};
	public static boolean switching = false;
	public static Generator generator;
	public static int aggNumber = 1; //number of modelled aggregators
	
	private static int[][] nConTypes = new int[][]{
		//TYPES 1,2,3,4,5,6,7,8, 9, 10
		{1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, //DOMESTIC TYPE
		{1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, //COMMERCIAL SECTOR
		{1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, //INDUSTRIAL SECTOR
		{0, 0, 0, 0, 0, 0, 0, 1, 0, 0}  //TRANSPORT SECTOR
						};

	//matrix specifying the presence of technology for each consumer subtype
	private static int[][] conSubTypes = new int[][]{
		//COLUMNS CORRESSPOND TO TECHNOLOGIES:
		//         HP,RH,PV,TES,ES,EV
		/*TYPE 0*/ {0, 0, 0, 0, 0, 0}, //subtype which has not technology
		/*TYPE 1*/ {1, 0, 0, 0, 0, 0}, //subtype with HP
		/*TYPE 2*/ {1, 0, 0, 1, 0, 0}, //subtype with HP ad TES
		/*TYPE 3*/ {0, 1, 0, 0, 0, 0}, //subtype with RH
		/*TYPE 4*/ {0, 1, 0, 1, 0, 0}, //subtype with RH ad TES
		/*TYPE 5*/ {0, 0, 1, 0, 0, 0}, //subtype with PV
		/*TYPE 6*/ {0, 0, 1, 0, 1, 0}, //subtype with PV and ES
		/*TYPE 7*/ {0, 0, 0, 0, 1, 0}, //subtype with ES
		/*TYPE 8*/ {1, 0, 1, 1, 1, 0}, //subtype with HP, PV, TES and ES
		/*TYPE 9*/ {0, 1, 1, 1, 1, 0} //subtype with RH, PV, TES and ES
		///*TYPE10*/ ,{1, 0, 1, 1, 1, 1}  //subtype with HP,PV,TES,ES,EV
						}; 
	//LISTS OF AGENTS
	public static ArrayList<Consumer24> consumers = new ArrayList<Consumer24>();
	static ArrayList<Aggregator24> aggregators = new ArrayList<Aggregator24>();

	//COORDINATION PARAMETERS
	public static String coordType = "centralised"; //"centralised" for aggregator coordination, "distributed" for consumer coordination
	public static String coordObj = "DF"; //"price" for cost minimisation, "DF" for load smoothing
	
	//DIRECTORY TO WRITE DATA
	public static String dir = "C:/Users/Dina/Dropbox/UCL/PhD/TeslaPowerModel/Raw data/unprocessed/";
	public static double[][] tariffs = new double[2][aggNumber];
	
	public static void main(String[] args) throws IOException{
		//ARRAYS TO HOLD PRICE INFORMATION
		double[] prices = new double[24]; //wholesale prices
		
		long startTime = System.nanoTime();

		DataClass dataClass = new DataClass();
		
		long endTime = System.nanoTime();

		long duration = (endTime - startTime);
		System.out.println("DataClass method took " + duration/1000000);
		
		//CREATING AGENTS
		SystemOperator SO = new SystemOperator();

		for(int i=0; i<aggNumber; i++){
			Aggregator24 aggregator = new Aggregator24(i);
			aggregators.add(aggregator);
		}
		
		startTime = System.nanoTime();
		
		for(double DSM : DSMresponseRates){	
			DSMrespondRate=DSM; //SET RESPONSE RATE
			int Ntypes = nConTypes.length; //NUMBER OF CONSUMER SECTORS
			int Nsubtypes = conSubTypes.length; //NUMBER OF CONSUMER TYPES
			int c=-1; //INITIAL CONSUMER COUNT
			for(int i=0; i<Ntypes;i++){ //0=DOMESTIC, 1=COMMERCIAL, 2=INDUSTRIAL, 3=TRANSPORT
				for(int j=0; j<Nsubtypes; j++){ //
					int n=nConTypes[i][j];//n= NUMBER OF CONSUMERS OF SECTOR i AND TYPE j
					for(int k=0; k<n; k++){ //k= CONSUMER COUNT FOR CREATING THEM
						double DSMthreshold = DSMrespondRate*n;
						boolean DSMrespond=false;
						if(k<DSMthreshold){
							DSMrespond=true;
						}
						c+=1; //CONSUMER COUNTER
						int l = c % aggNumber;//EVENLY SPLIT CONSUMERS BETWEEN SUPPLIERS
						//Consumer24( consumer counter, number of consumers of this type and subtype, String name, time of optimisation start, consumer type, HP, PV, TES, ES, EV)
						Consumer24 consumer = new Consumer24(l, k, n, conNames[i], 0, i, j, conSubTypes, DSMrespond);
						consumers.add(consumer);//ADDING CONSUMER TO THE LIST
					}
				}
			}
			endTime = System.nanoTime();	
			duration = (endTime - startTime);
			System.out.println("Creating agents took " + duration/1000000);
			
		    //SETTING SCENARIO: 0=STEADY STATE, 1=TWO DEGREES, 2=SLOW PROGRESSION, 3=CONSUMER POWER
		    for (int s = 0; s < 1; s++){
		    	
		    	//int DR = (int) (DSM*100);
		    	//dir = "C:/Users/Dina/Dropbox/UCL/PhD/TeslaPowerModel/Raw data/unprocessed/"+scenarios[s]+DR+"/";
		    
		    	startTime = System.nanoTime();
		    	setScenario(s);
		    
		    	generator = new Generator(scenario);
			
		    	endTime = System.nanoTime();
		    	duration = (endTime - startTime);
		    	System.out.println("Creating generator took " + duration/1000000);
			
		    	//AGENTS CREATE FILES FOR STORING INFORMATION
		    	SO.createFile(s, consumers, aggregators);
			
		    	for(final Aggregator24 agg:aggregators){
		    		agg.createFile(s);
		    	}
			
		    	generator.createFile(scenario);
	
		    	//SET STORAGE SCENARIO: 0=STEADY STATE, 1=TWO DEGREES, 2=SLOW PROGRESSION, 3=CONSUMER POWER
		    	//for(int storage = 0; storage < 1 ; storage++){
		    	if(s==1){
		    		storage=3;
		    	}else{
		    		storage=scenario;
		    	}
					setSt(storage);
					for(int y=startYear; y<simLength+startYear; y+=5){
						long startTimeDay1 = System.nanoTime();	

						year=y;
						//calculate available technologies for the year
						startTime = System.nanoTime();
						dataClass.setTechValues(scenario, storage, year);
						endTime = System.nanoTime();
						duration = (endTime - startTime);
						System.out.println("Setting tech values took " + duration/1000000);
					
						//assign technology to consumers
						startTime = System.nanoTime();
						dataClass.allocateConsumers();
						endTime = System.nanoTime();
						duration = (endTime - startTime);
						System.out.println("Allocating consumers took " + duration/1000000);
					
						//AGENTS UPDATE WITH NEW DATA BASED ON NATIONAL & STORAGE SCENARIOS & YEAR
						generator.populateMeritOrder(scenario, year);
						
						for(final Consumer24 con: consumers){
							con.setScenarioParams(scenario, storage, year, DataClass.nConMultipliers, nConTypes, DataClass.correctionFactors); //consumers make predictions for demand and generation
						}
						for(final Aggregator24 agg:aggregators){
							agg.setScenarioParams(scenario, storage, year);
						}
						generator.setScenarioParams(scenario, storage, year);
						SO.setScenarioParams(scenario,year);
					
						//DAILY ACTIONS 
						for(int d=startDay; d<startDay+365; d++){
							long startTimeHour1 = System.nanoTime();
							day=d;
							//AGENTS INITIALISE FOR THE DAY AHEAD
							for(final Consumer24 con: consumers){
								//CONSUMERS PREDICT DEMAND
								con.initialise(day); //consumers make predictions for demand and generation
							}
							for(final Aggregator24 agg: aggregators){
								//AGGREGATORS PREDICT DEMAND
								agg.initialise(consumers,day); //aggregator creates consumers and consumers initialised demand
							}
							generator.initialise(day);
							//SO PREDICTS DEMAND
							SO.calculateConsumerSolar(consumers);
							SO.aggregateDemand(aggregators);
							SO.predictDemand(day);
							//GENERATOR MAKES A PREDICTION OF RENEWABLE GENERATION
							generator.predictRES(day);
							//SO SCHEDULES GENERATORS BASED ON RES AND SYSTEM DEMAND 
							SO.scheduleGenerators(generator);
							prices = generator.calculateWholesalePrices(SO.getAggDemand());
				
							//COORDINATION
							if(storage == -1){
								//IF STORAGE SET TO -1 DO NOTHING
							}else{
								//SELECT FOR CENTRALISED SCHEDULING
								if(coordType == "centralised"){
									if(coordObj == "priceMin"){
										SO.scheduleAll(aggregators, prices);
									}else{
										for(final Aggregator24 agg:aggregators){
											if(coordObj == "price"){
												agg.scheduleOnPrice(prices);
											}else{
												agg.scheduleDF();
											}
										}
									}
								}else{
								//SELECT FOR DECENTRALISED SCHEDULING
									for(final Consumer24 con: consumers){
										if(coordObj == "price"){
											if(con.DSMrespond == true){
												if(con.getMaxCap() > 0 || con.getMaxCapTherm() >0){
													/*if(con.getName().equals("industrial11110_0")){
														con.optimiseOnPrice(prices);
													}else{*/
														con.optimiseOnPrice(prices);
													//}
												}
											}
										}else{
											if(con.DSMrespond == true){
												if(con.getMaxCap() > 0 || con.getMaxCapTherm() >0){
													con.optimiseDF();
												}
											}
										}
									}
								}
							}
							for(final Aggregator24 agg:aggregators){
								agg.aggregateDemand();
								agg.calculateResidual();
							}
							//AFTER COORDINATION SO RECALCULATES SYSTEM DEMAND
							SO.calculateConsumerSolar(consumers);
							SO.aggregateDemand(aggregators);
				
							generator.readActualRES(SO.getImbalance());
							
							//SO RESCHEDULES GENERATORS & RECALCULATES PRICES
							SO.reScheduleGenerators(generator);
							SO.calculateResidual();
							SO.calculateImbalance();
							prices = generator.reCalculateWholesalePrices(SO.getAggDemand(), SO.getImbalance());
							//prices = generator.recalculateWholesalePrices(SO.getAggDemand());
							
							//AGENTS WRITE DOWN DATA
	
							for (final Aggregator24 agg: aggregators){
								agg.calculateTariffs(prices);
								tariffs[0][agg.getSupIndex()]=agg.getSupIndex();
								tariffs[1][agg.getSupIndex()]=agg.getTariff();
							}
							
							if(switching == true){
								for(int i=0; i<0.1*consumers.size(); i++){
									Random randomGenerator = new Random();
									int index = randomGenerator.nextInt(consumers.size());
									Consumer24 consumer = consumers.get(index);
									consumer.chooseSupplier(tariffs);
								}	
							}
						        
							generator.writeGenData(scenario, year, day, storage);
							SO.writeData(scenario, storage, year, day, prices , generator.getMargPrices(), consumers, aggregators);
							
							long endTimeHour24 = System.nanoTime();
	
							duration = (endTimeHour24 - startTimeHour1);
							System.out.println("Day "+ d + " took	 " + duration/1000000);
							}
						long endTimeDay365 = System.nanoTime();
						duration = (endTimeDay365 - startTimeDay1);
						System.out.println("Year " + year + " took " + duration/1000000);
						}
					}
		    	}
		    	consumers.clear();
			}
	//}

//FUNCTIONS
	public static int getSimLength() {
		return simLength;
	}

	public static void setSimLength(int simLength) {
		TeslaRun24.simLength = simLength;
	}
	
	public static int[][] getnConTypes() {
		return nConTypes;
	}

	public static int getSt() {
		return storage;
	}

	public static void setSt(int st) {
		TeslaRun24.storage = st;
	}

	public static int getS() {
		return scenario;
	}

	public static void setScenario(int s) {
		TeslaRun24.scenario = s;
	}

	public Generator getGenerator() {
		return generator;
	}
	

	public static int getStartYear() {
		return startYear;
	}

	public static void setStartYear(int startYear) {
		TeslaRun24.startYear = startYear;
	}
}