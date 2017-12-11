package Agents;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import Execute.TeslaRun24;
import Utils.Methods;

public class Aggregator24 {
	//private int length = TeslaRun24.getSimLength();
	private double[] totDemand = new double[24]; //total demand across all consumers
	private double[] aggDemand = new double[24]; //total residual demand of consumers
	private double[] aggResidual = new double[24]; //aggregator residual
	
	private double[] aggDemandPrevious = new double[24]; //total residual demand of consumers
	private double[] aggResidualPrevious = new double[24]; //aggregator residual
	private ArrayList<Consumer24> consumersAgg = new ArrayList<Consumer24>(); //list of all consumers coordinate by the aggregator

	private double[] prices = new double[24]; //wholesale prices received from the market
	private double[][] windCount = new double[4][36]; //Wind capacity scaling factor relative to 2015
	
	private double[] signal = new double[24];
	
	private double windGenData[][] = new double[365][24]; //annual wind generation data
	private double windGen[] = new double[24]; //daily wind generation profile
	private double windMultiplier; //wind scaling factor to simulate capacity increase
	
	private File file0, file1; //file for tracking coordination and file for storing demand
	private int supIndex;
	private double tariff;
	
	//Here aggregator represents the system operator
	public Aggregator24(int i){
		this.supIndex = i; //assign aggregator index for matching with consumers
		//reading(from where, where to save data, scaling parameter, matrix dimensions)
		//Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/windGenPower.txt", this.windGenData, 1, 365, 24); // wind generation profile for year 2015
		//Methods.readData("C:/Users/Dina/workspace/TeslaPowerWall24/Data/windCount.txt", this.windCount, 1, 4, 36); //wind farm scaling factor for the four scenarios
	}

	public void createFile(int scenario) {
		file0 = new File(TeslaRun24.dir+"aggregator" + supIndex + "_coordination.txt");
		//file1 = new File(TeslaRun24.dir+"aggregator" + supIndex + "_data.txt");
		//Methods.writeToFile(file1, "scenario,year,day,storage,hour,residual,demand");
	}
	
	public void predictWind(int day) {
		for (int i=0; i<24; i++){
			windGen[i]=windGenData[day][i] * windMultiplier;
		}
	}
	
	public void aggregateDemand() {
		Methods.zero(totDemand);
		Methods.zero(aggDemand);
		for(final Consumer24 con: consumersAgg){
			for (int i=0; i<24; i++){
				totDemand[i]+=con.getDemand()[i];//total system demand
				aggDemand[i]+=con.getResidual()[i];//sum of consumer residuals
			}
		}
	}

	public void calculateResidual(){
		for(int i=0; i<24; i++){
			aggResidual[i] = Math.max(0,aggDemand[i]-windGen[i]);//sum of consumer residuals - RES
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

	public void writeResidual(int scenario, int year, int day, int storage) throws IOException {
		Methods.writeToFile(file1, scenario + "," + year + ","+day+","+storage+"," + aggResidual, aggDemand);
	}

	public void initialise(ArrayList<Consumer24> consumers, int day){
		consumersAgg = allocateConsumers(consumers);
		aggregateDemand();
		//predictWind(day);
		calculateResidual();
	}
	
	
	
	private ArrayList<Consumer24> allocateConsumers(ArrayList<Consumer24> consumers) {
		ArrayList<Consumer24> conList = new ArrayList<Consumer24>();
		for (final Consumer24 con: consumers){
			if(con.getSup()==supIndex){
				conList.add(con);
			}
		}
		return conList;
	}
	
	public void setScenarioParams(int scenario, int storage, int year) {
		//this.windMultiplier = windCount[scenario][year];
	}

	public void scheduleDF() throws IOException {
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",before coordination,", getAggResidual());
		double n = 0;
		double LR=0.001,aveSignal=1,totalSignal=0;
		for(final Consumer24 con: consumersAgg){
			//System.out.println(Arrays.toString(con.getResidual()));
			n+=con.getConMultiplier();
		}
			for(int j=0; j<24; j++){
				signal[j] = aggResidual[j]/n;
				totalSignal+=signal[j];
			}aveSignal=totalSignal/24;
			LR=0.000788307 * aveSignal;
			for(int i=0; i<=4; i++){
			for(final Consumer24 con: consumersAgg){
				if(con.DSMrespond == true){
					if(con.getMaxCap()>0 || con.getMaxCapTherm()>0){
						/*String name = con.getName();
						if(name.equals("industrial11110_0") & i==4){
							con.scheduleDFCentral(signal, LR);
							con.updateDemand();
							con.setResidual();
						}else{*/
							con.scheduleDFCentral(signal, LR);
							con.updateDemand();
							con.setResidual();
						//}
					}
				}
			}aggregateDemand();
			calculateResidual();
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after iteration " + i + ",", getAggResidual());
		}
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after coordination,", getAggResidual());
	}
	
	public void scheduleOnPrice(double[] prices) throws IOException {
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",before coordination,", getAggResidual());
		for(int i=0; i<consumersAgg.size(); i++){
			for(int j=0; j<24; j++){
				signal[j]=aggResidual[j]*prices[j];
			}
			Consumer24 con = consumersAgg.get(i);
				if(con.DSMrespond == true){
					if(con.getMaxCap()>0 || con.getMaxCapTherm()>0){
						con.optimiseOnPrice(signal);
						aggregateDemand();
						calculateResidual();
						Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after iteration " + i + ",", getAggResidual());
					}
			}
		}
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after coordination,", getAggResidual());
	}
	
	public void scheduleOnPrice2(double[] prices) throws IOException {
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",before coordination,", getAggResidual());
		double n = 0;
		double LR=0.25;
		for(final Consumer24 con: consumersAgg){
			n+=con.getConMultiplier();
		}
		for(int l=0; l<10; l++){
			for(int j=0; j<24; j++){
				signal[j] = aggResidual[j]*prices[j]/n;
			}
			for(int i=0; i<consumersAgg.size(); i++){
				Consumer24 con = consumersAgg.get(i);
					if(con.getMaxCap()>0 ||con.getMaxCapTherm()  > 0 & con.DSMrespond == true){
						con.scheduleDFCentral(signal,LR);
						con.updateDemand();
						con.setResidual();
					}
			}
			aggregateDemand();
			calculateResidual();
			Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after iteration " + l + ",", getAggResidual());
		}
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after coordination,", getAggResidual());
	}

	public void scheduleOnPriceMin(double[] prices, int i) {
		double LR=0.02;
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",before coordination,", aggResidual);
		for(final Consumer24 con: consumersAgg){
			if(con.DSMrespond == true){
				con.scheduleOnPriceCentral(prices, LR);
				con.updateDemand();
				con.setResidual();
			}
		}aggregateDemand();
		calculateResidual();
		Methods.writeArrayToFile(file0, TeslaRun24.scenario +","+ TeslaRun24.year +","+TeslaRun24.day+","+TeslaRun24.storage+",after iteration " + i + ",", aggResidual);
	}

	public void returnToPreviousValues() {
		for(final Consumer24 con: consumersAgg){
			con.setResidual(con.getResidualPrevious().clone());
			con.setDemand(con.getDemandPrevious().clone());
		}
		aggregateDemand();
		calculateResidual();
	}

	public double[] getPrices() {
		return prices;
	}

	public double[] getAggResidualPrevious() {
		return aggResidualPrevious;
	}

	public void setAggResidualPrevious(double[] aggResidual1) {
		this.aggResidualPrevious = aggResidual1;
	}

	public void setAggResidual(double[] aggResidual) {
		this.aggResidual = aggResidual;
	}

	public double[] getAggDemandPrevious() {
		return aggDemandPrevious;
	}

	public void setAggDemandPrevious(double[] aggDemand1) {
		this.aggDemandPrevious = aggDemand1;
	}

	public void setAggDemand(double[] aggDemand) {
		this.aggDemand = aggDemand;
	}

	public int getSupIndex() {
		return supIndex;
	}

	public void calculateTariffs(double[] prices) {
		double cost = 0,totLoad = 0;
		for(int i=0; i<24; i++){
			cost+=aggDemand[i]*prices[i];
			totLoad += aggDemand[i];
		}this.tariff=cost/totLoad;
	}

	public double getTariff() {
		return tariff;
	}
}