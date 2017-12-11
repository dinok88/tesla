package Agents;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import Execute.TeslaRun24;
import Utils.Methods;

public class SystemOperator {
	//private int length = TeslaRun24.getSimLength();
	private double[] totDemand = new double[24]; //total demand across all aggregators
	private double[] predictedDemand = new double[24]; //total residual demand of aggregators
	private double[] imbalance = new double[24]; //total residual demand of aggregators
	private double[] aggDemand = new double[24]; //total residual demand of aggregators
	private double[] aggResidual = new double[24]; //SO residual
	private double[] consumerSolarGen = new double[24];
	private double[][] pastDemand = new double[365][24];
	private double[][] losses = new double[4][36];
	private double loss;

	private double[] prices = new double[24]; //wholesale prices received from the market
	private double[] storageCap = new double[36]; //total storage capacity across all consumers
	Generator generator;

	private File SOcoordFile, sysDataFile, conSuppliersFile; //system files
	private File conDemandFile, conUsedRESFile, conCurtRESFile, conThermDemandFile, conResidualFile, conMultFile; //consumer files
	private File aggDemandFile, aggTariffsFile; //aggregator files
	
	//Here aggregator represents the system operator
	public SystemOperator(){
		Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/TDlosses.txt", this.losses, 1, 4, 36);//carbon prices exist for the 4 scenarios
		}
	
	public void createFile(int scenario, ArrayList<Consumer24> consumers, ArrayList<Aggregator24> aggregators) {
		SOcoordFile = new File(TeslaRun24.dir+"SOcoordination.txt");
		Methods.writeToFile(SOcoordFile, "scenario,year,day,storage, demand type, hour 1, hour 2, hour 3, hour 4, hour 5, hour 6, hour 7, hour 8, hour 9, hour 10, hour 11, hour 12, hour 13, hour 14, "
				+ "hour 15, hour 16, hour 17, hour 18, hour 19, hour 20, hour 21, hour 22, hour 23, hour 24");
		sysDataFile = new File(TeslaRun24.dir+"system_data.txt");//aggregate demand
		Methods.writeToFile(sysDataFile, "scenario,storage,year,day,hour,residual,demand,prices,margPrices,imbalance,losses");
		//for consumers
		conDemandFile = new File(TeslaRun24.dir+"AllConsumerDemand.txt");
		conSuppliersFile = new File(TeslaRun24.dir+"AllConsumerSup.txt");
		conThermDemandFile = new File(TeslaRun24.dir+"AllConsumerThermalDemand.txt");
		conResidualFile = new File(TeslaRun24.dir+"AllConsumerResidual.txt");
		conMultFile = new File(TeslaRun24.dir+"AllConsumerMultipliers.txt");
		String header1 = "scenario,storage,year,day,hour";
		String header2 = "scenario,storage,year,day";
		String conHeader1 = header1, conHeader2 = header2, aggHeader1 = header1, aggHeader2 = header2;
		for(final Consumer24 con: consumers){
			conHeader1 = conHeader1 + "," + con.getName();
			conHeader2 = conHeader2 + "," + con.getName();
		}
		Methods.writeToFile(conDemandFile, conHeader1);
		Methods.writeToFile(conSuppliersFile, conHeader2);
		Methods.writeToFile(conThermDemandFile, conHeader1);
		Methods.writeToFile(conResidualFile, conHeader1);
		Methods.writeToFile(conMultFile, conHeader1);
		conUsedRESFile = new File(TeslaRun24.dir+"AllConsumerUsedRES.txt");
		Methods.writeToFile(conUsedRESFile, conHeader1);
		conCurtRESFile = new File(TeslaRun24.dir+"AllConsumerCurtRES.txt");
		Methods.writeToFile(conCurtRESFile, conHeader1);
		//aggregators
		aggDemandFile = new File(TeslaRun24.dir+"AllAggregatorDemand.txt");
		aggTariffsFile = new File(TeslaRun24.dir+"AllAggregatorTariffs.txt");
		for(final Aggregator24 agg: aggregators){
			aggHeader1 = aggHeader1 + "," + "Sup_" + agg.getSupIndex();
			aggHeader2 = aggHeader2 + "," + "Sup_" + agg.getSupIndex();
		}
		Methods.writeToFile(aggDemandFile, aggHeader1);
		Methods.writeToFile(aggTariffsFile, aggHeader2);
	}
	
	public void setScenarioParams(int scenario, int year){
		this.loss = losses[scenario][year];
	}
	
	public void aggregateDemand(ArrayList<Aggregator24> aggregators) {
		Methods.zero(totDemand);
		Methods.zero(aggDemand);
		for(final Aggregator24 agg: aggregators){
			for (int i=0; i<24; i++){
				totDemand[i]+=agg.getAggDemand()[i];//total system demand
				aggDemand[i]+=agg.getAggResidual()[i];//sum of consumer residuals
			}
		}
		//take away consumer generated solar
		for(int i=0 ; i<24; i++){
			totDemand[i] += loss;
			aggDemand[i]=aggDemand[i] - consumerSolarGen[i] + loss;
		}
	}

	public void calculateResidual(){
		double[] usedRES = Generator.getUsedRES().clone();
		for(int i=0; i<24; i++){
			aggResidual[i] = Math.max(aggDemand[i]-usedRES[i],0);//sum of consumer residuals - RES
		}
	}

	public double[] getDemand() {
		return totDemand;
	}
	
	public double[] getAggDemand() {
		return aggDemand;
	}
	
	public double[] getAggResidual() {
		return aggResidual;
	}

	public double[] getPrices() {
		return prices;
	}

	public void aggregateStorage(ArrayList<Consumer24> consumers, int year) {
		for (final Consumer24 con: consumers){
			getStorageCap()[year]+=con.getMaxCap();
		}
	}
	
	   public void scheduleAll(ArrayList<Aggregator24> aggregators, double[] prices2) {
			Generator gen = TeslaRun24.generator;
			Methods.writeArrayToFile(SOcoordFile, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",before coordination,", aggResidual);
			
			double cost1=0, cost2=0;
			int i=0;
			for (int j=0; j<24; j++){
				cost1+=prices2[j]*aggResidual[j];
			}
			while(i!=10)
			{
				for(final Aggregator24 agg: aggregators){
					agg.scheduleOnPriceMin(prices2, i);
				}
				aggregateDemand(aggregators);
				calculateResidual();
				prices2 = gen.calculateWholesalePrices(aggDemand).clone();
				Methods.writeArrayToFile(SOcoordFile, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after iteration " + i + ",", aggResidual);
				cost2=0;
				//calculating the cost of power for the system
				for (int j=0; j<24; j++){
					cost2+=prices2[j]*aggResidual[j];
				}
				if(cost2<cost1){
					i+=1;
				}else{
					for(final Aggregator24 agg: aggregators){
						agg.returnToPreviousValues();
					}
					aggregateDemand(aggregators);
					calculateResidual();
					break;
				}	
				cost1=cost2;
			}
			Methods.writeArrayToFile(SOcoordFile, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after coordination,", getAggResidual());
	}

	public void updateResidual(double[] previousResidual, double[] previousDemand) {
		for(int i=0; i<24; i++){
			aggResidual[i] = previousResidual[i];
			aggDemand[i] = previousDemand[i];
		}
	}

	public double[] getStorageCap() {
		return storageCap;
	}

	public void setStorageCap(double[] storageCap) {
		this.storageCap = storageCap;
	}

	public void setAggResidual(double[] aggResidual) {
		this.aggResidual = aggResidual;
	}

	public void setAggDemand(double[] aggDemand){
		this.aggDemand = aggDemand;
	}

	public void writeData(int scenario, int storage, int year, int day, double[] prices, double[] margPrices, ArrayList<Consumer24> consumers, ArrayList<Aggregator24> aggregators) throws IOException {
		String titles = scenario + "," + storage + "," + year + "," + day;
		for(int i=0; i<24; i++){
			pastDemand[day][i]=aggDemand[i];
		}
		Methods.writeToFile(sysDataFile, titles , aggResidual, aggDemand, prices, margPrices, imbalance, loss);
		Methods.writeToFileConsumerData(conDemandFile, conUsedRESFile, conCurtRESFile, conSuppliersFile, 
				conThermDemandFile, conResidualFile, conMultFile, titles, consumers);
		Methods.writeToFileAggData(aggDemandFile, aggTariffsFile, titles, aggregators);
	}

	public void scheduleGenerators(Generator generator) {
		generator.scheduleGenerators(aggDemand);
		
	}

	public void reScheduleGenerators(Generator generator2) {
		generator2.reScheduleGenerators(aggDemand);
	}

	public void predictDemand(int day) {
		if(TeslaRun24.year==TeslaRun24.startYear){
			//do nothing
		}else{
			double[] predictedDemand = aggDemand.clone();
			for(int i=0; i<24; i++){
				aggDemand[i]=0.5*(pastDemand[day][i] + predictedDemand[i]);
			}
		}
		predictedDemand=aggDemand.clone();
	}

	public void calculateConsumerSolar(ArrayList<Consumer24> consumers) {
		Methods.zero(consumerSolarGen);
		for(final Consumer24 con: consumers){
			if(con.PV==true){
				for(int i=0; i<24; i++){
					consumerSolarGen[i]+=con.getCurtRES()[i];
				}
			}
		}
	}

	public void calculateImbalance() {
		for(int i=0; i<24; i++){
			imbalance[i] +=aggDemand[i]-predictedDemand[i];
		}
	}

	public double[] getImbalance() {
		return imbalance;
	}
}