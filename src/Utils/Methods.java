package Utils;
import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.StringTokenizer;

import Agents.Aggregator24;
import Agents.Consumer24;

public class Methods {
	private static FileReader fr;
    private static BufferedReader br;
    private static FileWriter fw;
    private static StringTokenizer strtok;
    private static String s;

	public static void scheduleSystemStorageOnPrice(
			double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, //electrical and thermal power constraints
			double effEle, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			double[] demandEleBL, 
			double[] genProfile,
			double initEleStore, int t1){
		try {
			IloCplex cplex = new IloCplex ();
			
			//decision variables
			IloNumVar[] chargeEle = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			
			for (int i=0; i<24; i++){
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total flexible electrical demand by consumer
		
			//calculate net charged energy in electrical and thermal storage
			for(int i=0; i<24; i++){
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1,dischargeEle[i])); //net storage for battery
				//total load = charge from EV + demand from HP + demand from electrical storage - discharge of electrical storage
				totalLoad[i]=cplex.sum(demandEleBL[i]-genProfile[i],cplex.sum(chargeEle[i], cplex.prod(-1,dischargeEle[i]))); //total flexible load
			}
			
			//initial amount of energy in the thermal and electrical stores passed from previous day
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]); 
			
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
			}
			
			IloNumExpr x1 = cplex.prod(signal[0], totalLoad[0]);
			IloNumExpr objective = x1;
	
			for(int i=1; i<24; i++){
				IloNumExpr objective1 = objective;
				x1 = cplex.prod(signal[i], totalLoad[i]);
				objective = cplex.sum(objective1, x1);
			}
			
			//define objective
			cplex.addMinimize(objective);
			
			//CONSTRAINTS
			
			for(int i=0; i<24; i++){
				//stored energy cannot exceed storage constraints
				cplex.addLe(availableEleEnergy[i], maxEleCap);
				cplex.addLe(0, availableEleEnergy[i]);
				cplex.addLe(cplex.sum(chargeEle[i], dischargeEle[i]), maxElePower);
				cplex.addLe(0, totalLoad[i]);
			}
			
				cplex.addEq(availableEleEnergy[23], initEleStore);
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int i=0; i<24; i++){
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					storageEleProfile[i] = chargeEleProfile[i]-dischargeEleProfile[i];
				}
				
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void scheduleSystemStorageDF(
			double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, //electrical and thermal power constraints
			double effEle, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			double[] demandEleBL, 
			double[] genProfile, double aveRes,
			double initEleStore, int t1){
		try {
			IloCplex cplex = new IloCplex ();
			cplex.setOut(null);
			
			//decision variables
			IloNumVar[] chargeEle = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			
			for (int i=0; i<24; i++){
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total flexible electrical demand by consumere
		
			//calculate net charged energy in electrical and thermal storage
			for(int i=0; i<24; i++){
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1,dischargeEle[i])); //net storage for battery
				totalLoad[i]=cplex.sum(demandEleBL[i]-genProfile[i],cplex.sum(chargeEle[i], cplex.prod(-1,dischargeEle[i]))); //total flexible load
			}
			
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]);
			
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
			}
			
			double n = 1d/24.0;
			
			IloNumExpr x1 = cplex.prod(totalLoad[0], totalLoad[0]);
			IloNumExpr x2 = cplex.prod(-2*aveRes, totalLoad[0]);
			double x3 = aveRes*aveRes;
			IloNumExpr objective = cplex.sum(x1, cplex.sum(x2, x3)) ;

			IloNumExpr objective1; 
			for(int i=1; i<24; i++){
				objective1=objective;
				x1 = cplex.prod(totalLoad[i], totalLoad[i]);
				x2 = cplex.prod(-2*aveRes, totalLoad[i]);
				objective = cplex.sum(x1, cplex.sum(x2, x3), objective1) ;
			}
			objective1=cplex.prod(n, objective);
			
			//define objective
			cplex.addMinimize(objective1);
			
			//CONSTRAINTS
			
			for(int i=0; i<24; i++){
				//stored energy cannot exceed storage constraints
				cplex.addLe(availableEleEnergy[i], maxEleCap);
				cplex.addLe(0, availableEleEnergy[i]);
				cplex.add(cplex.ifThen(cplex.ge(chargeEle[i],0.0000001), cplex.eq(dischargeEle[i],0)));
				//cplex.addLe(0, totalLoad[i]);
				}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int i=0; i<24; i++){
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					storageEleProfile[i] = chargeEleProfile[i]-dischargeEleProfile[i];
				}
				
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void optimiseDemandDFCentral(
			double maxHeatCap, double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, double maxThermPower, //electrical and thermal power constraints
			double maxHPower, //nominal capacity for the heat pump 
			double effEle, double COP, double effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			double[] demandProfileHP,//charging and discharging profile for the battery
			double[] heatDemand, double[] demandEleBL, double[] flexDemand0,
			double[] genProfile,
			double initEleStore, double initHeatStore, double LR, int t1,
			double SOC1, double SOC2, double[] demandEV, double EVcap,
			double maxEVpower, int EVstart, int EVfinish, double effEV, String name){
		try {
			IloCplex cplex = new IloCplex ();
			cplex.setOut(null);
			
			double[] chargeThermProfile = new double[24];
			double[] dischargeThermProfile = new double[24];
			double EVenergy = (SOC2-SOC1)*EVcap;
			
			//determining the number of hours for charging
			int EVlength;
			if(EVfinish>EVstart){
				EVlength=EVfinish-EVstart;
			}else{
				EVlength=EVfinish+24-EVstart;
			}
			
			//decision variables
			IloNumVar[] chargeEV = new IloNumVar[24]; // charge by EV
			IloNumVar[] chargeEle = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			IloNumVar[] chargeTherm = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeTherm = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			
			for (int i=0; i<24; i++){
				if(i<=EVlength){
					chargeEV[(EVstart + i)%24] = cplex.numVar(0, maxEVpower);//EV charge is bound by the EV power constraints
				}else{
					chargeEV[(EVstart + i)%24] = cplex.numVar(0, 0);
				}
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
				chargeTherm[i] = cplex.numVar(0, maxThermPower); //charge P is bound by maxP
				dischargeTherm[i] = cplex.numVar(0, maxThermPower);
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] availableHeatEnergy = new IloNumExpr[24]; //refers to the energy in the thermal storage
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			IloNumExpr[] netHeatEnergy = new IloNumExpr[24]; //net demand for power by thermal store
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total flexible electrical demand by consumer
			IloNumExpr[] demandEleHP = new IloNumExpr[24]; //thermal energy going into thermal storage
			IloNumExpr[] demandThermHP = new IloNumExpr[24]; //thermal energy going into thermal storage
			
			double m=1d/COP;
			
			//electrical HP demand converted to heat demand using COP (either from the battery of from HP)
			for(int i=0; i<24; i++){
				//total thermal demand from HP
				demandThermHP[i]=cplex.sum(cplex.sum(heatDemand[i],chargeTherm[i]),cplex.prod(-1, dischargeTherm[i]));
				//total electricity demand from HP
				demandEleHP[i]=cplex.prod(m, demandThermHP[i]); 
			}
		
			//calculate net charged energy in electrical and thermal storage
			for(int i=0; i<24; i++){
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1,dischargeEle[i])); //net storage for battery
				netHeatEnergy[i]=cplex.sum(cplex.prod(chargeTherm[i],effTherm), cplex.prod(-1,dischargeTherm[i]));//net storage for thermal store
				//total load = charge from EV + demand from HP + demand from electrical storage - discharge of electrical storage
				if(name.equals("transport")){
					totalLoad[i]=cplex.sum(chargeEle[i],-genProfile[i]);
				}else{
					totalLoad[i]=cplex.sum(chargeEV[i],cplex.sum(demandEleBL[i]-genProfile[i],cplex.sum(demandEleHP[i], cplex.sum(chargeEle[i], cplex.prod(-1,dischargeEle[i]))))); //total flexible load
				}
			}
			
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]);
			availableHeatEnergy[t1] = cplex.sum(initHeatStore, netHeatEnergy[t1]);  
			
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
				availableHeatEnergy[i] = cplex.sum(availableHeatEnergy[k],netHeatEnergy[i]);
			}
			
			IloNumExpr x1 = cplex.prod(signal[0], totalLoad[0]);
			IloNumExpr x2 = cplex.prod(totalLoad[0], totalLoad[0]);
			IloNumExpr x3 = cplex.prod(LR, x2);
			IloNumExpr x4 = cplex.prod(-2*LR, cplex.prod(flexDemand0[0], totalLoad[0]));
			double x5 = LR * flexDemand0[0] * flexDemand0[0];
			IloNumExpr objective = cplex.sum(x5, cplex.sum(x1, x3, x4)) ;
	
			for(int i=1; i<24; i++){
				IloNumExpr objective1 = objective;
				x1 = cplex.prod(signal[i], totalLoad[i]);
				x2 = cplex.prod(totalLoad[i], totalLoad[i]);
				x3 = cplex.prod(LR, x2);
				x4 = cplex.prod(-2*LR, cplex.prod(flexDemand0[i], totalLoad[i]));
				x5 = LR * flexDemand0[i] * flexDemand0[i];
				objective = cplex.sum(cplex.sum(x5, cplex.sum(x1, x3, x4)), objective1) ;
			}
			
			//define objective
			cplex.addMinimize(objective);
			
			//CONSTRAINTS
			IloNumExpr totEVcharge = cplex.prod(chargeEV[0],effEV);
			IloNumExpr totEVcharge2;
			for(int i=1; i<24; i++){
				totEVcharge2 = cplex.sum(totEVcharge, cplex.prod(chargeEV[i],effEV));
				totEVcharge = totEVcharge2;
			}
			
			for(int i=0; i<24; i++){
				//HP demand is limited by max and min power constraints
				cplex.addLe(demandEleHP[i],maxHPower);
				cplex.addLe(0,demandEleHP[i]);
				//stored energy cannot exceed storage constraints
				cplex.addLe(availableEleEnergy[i], maxEleCap);
				cplex.addLe(availableHeatEnergy[i], maxHeatCap);
				cplex.addLe(0, availableEleEnergy[i]);
				cplex.addLe(0, availableHeatEnergy[i]);
				if(name.equals("transport")){
					cplex.addEq(dischargeEleProfile[i],dischargeEle[i]);
				}else{
					cplex.add(cplex.ifThen(cplex.ge(chargeEle[i],0.001),cplex.eq(dischargeEle[i],0)));
				}
				//cplex.addLe(0, totalLoad[i]);
				cplex.addEq(totEVcharge, EVenergy);
			}
			
			//cplex.addEq(initEleStore, availableEleEnergy[23]);
			//cplex.addEq(initHeatStore, availableHeatEnergy[23]);
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int i=0; i<24; i++){
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					if(name.equals("transport")){
						storageEleProfile[i] = chargeEleProfile[i];
					}else{
						chargeThermProfile[i]=cplex.getValue(chargeTherm[i]);
						dischargeThermProfile[i]=cplex.getValue(dischargeTherm[i]);
						demandProfileHP[i]=(heatDemand[i]+chargeThermProfile[i]-dischargeThermProfile[i])/COP;
						demandEV[i]=cplex.getValue(chargeEV[i]);
						storageEleProfile[i] = chargeEleProfile[i]-dischargeEleProfile[i];
					}
				}
				
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				initHeatStore=cplex.getValue(availableHeatEnergy[23]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void optimiseDemandOnPriceDistrib(
			double maxHeatCap, double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, double maxThermPower, //electrical and thermal power constraints
			double maxHPower, //nominal capacity for the heat pump 
			double effEle, double COP, double effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			double[] demandProfileHP,//charging and discharging profile for the battery
			double[] heatDemand, double[] demandEleBL, 
			double[] genProfile,
			double initEleStore, double initHeatStore, int t1,
			double SOC1, double SOC2, double[] demandEV, double EVcap,
			double maxEVpower, int EVstart, int EVfinish, double effEV, String name){
		try {
			IloCplex cplex = new IloCplex ();
			cplex.setOut(null);
			
			double[] chargeThermProfile = new double[24];
			double[] dischargeThermProfile = new double[24];
			double EVenergy = (SOC2-SOC1)*EVcap;
			
			//determining the number of hours for charging
			int EVlength;
			if(EVfinish>EVstart){
				EVlength=EVfinish-EVstart;
			}else{
				EVlength=EVfinish+24-EVstart;
			}
			
			//decision variables
			IloNumVar[] chargeEV = new IloNumVar[24]; // charge by EV
			IloNumVar[] chargeEle = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			IloNumVar[] chargeTherm = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeTherm = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			
			for (int i=0; i<24; i++){
				if(i<=EVlength){
					chargeEV[i] = cplex.numVar(0, maxEVpower);//EV charge is bound by the EV power constraints
				}else{
					chargeEV[i] = cplex.numVar(0, 0);
				} 
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
				chargeTherm[i] = cplex.numVar(0, maxThermPower); //charge P is bound by maxP
				dischargeTherm[i] = cplex.numVar(0, maxThermPower);
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] availableHeatEnergy = new IloNumExpr[24]; //refers to the energy in the thermal storage
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			IloNumExpr[] netHeatEnergy = new IloNumExpr[24]; //net demand for power by thermal store
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total flexible electrical demand by consumer
			IloNumExpr[] demandEleHP = new IloNumExpr[24]; //thermal energy going into thermal storage
			IloNumExpr[] demandThermHP = new IloNumExpr[24]; //thermal energy going into thermal storage
			
			double m=1d/COP;
			
			//electrical HP demand converted to heat demand using COP (either from the battery of from HP)
			for(int i=0; i<24; i++){
				//total thermal demand from HP
				demandThermHP[i]=cplex.sum(cplex.sum(heatDemand[i],chargeTherm[i]),cplex.prod(-1, dischargeTherm[i]));
				//total electricity demand from HP
				demandEleHP[i]=cplex.prod(m, demandThermHP[i]); 
			}
		
			//calculate net charged energy in electrical and thermal storage
			for(int i=0; i<24; i++){
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1,dischargeEle[i])); //net storage for battery
				netHeatEnergy[i]=cplex.sum(cplex.prod(chargeTherm[i],effTherm), cplex.prod(-1,dischargeTherm[i]));//net storage for thermal store
				//total load = charge from EV + demand from HP + demand from electrical storage - discharge of electrical storage
				if(name.equals("transport")){
					totalLoad[i]=cplex.sum(chargeEle[i],-genProfile[i]);
				}else{
					totalLoad[i]=cplex.sum(chargeEV[i],cplex.sum(demandEleBL[i]-genProfile[i],cplex.sum(demandEleHP[i], cplex.sum(chargeEle[i], cplex.prod(-1,dischargeEle[i]))))); //total flexible load
				}
			}
			
			//initial amount of energy in the thermal and electrical stores passed from previous day
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]); 
			availableHeatEnergy[t1] = cplex.sum(initHeatStore, netHeatEnergy[t1]);
			
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
				availableHeatEnergy[i] = cplex.sum(availableHeatEnergy[k],netHeatEnergy[i]);
			}
			
			IloNumExpr x1 = cplex.prod(signal[0], totalLoad[0]);
			IloNumExpr objective = x1;
	
			for(int i=1; i<24; i++){
				IloNumExpr objective1 = objective;
				x1 = cplex.prod(signal[i], totalLoad[i]);
				objective = cplex.sum(objective1, x1);
			}
			
			//define objective
			cplex.addMinimize(objective);
			
			//CONSTRAINTS
			IloNumExpr totEVcharge = cplex.prod(chargeEV[0],effEV);
			IloNumExpr totEVcharge2;
			for(int i=1; i<24; i++){
				totEVcharge2 = cplex.sum(totEVcharge, cplex.prod(chargeEV[i],effEV));
				totEVcharge = totEVcharge2;
			}
			
			for(int i=0; i<24; i++){
				//HP demand is limited by max and min power constraints
				cplex.addLe(demandEleHP[i],maxHPower);
				cplex.addLe(0,demandEleHP[i]);
				//stored energy cannot exceed storage constraints
				cplex.addLe(availableEleEnergy[i], maxEleCap);
				cplex.addLe(availableHeatEnergy[i], maxHeatCap);
				cplex.addLe(0, availableEleEnergy[i]);
				cplex.addLe(0, availableHeatEnergy[i]);
				if(name.equals("transport")){
					cplex.addEq(dischargeEleProfile[i],dischargeEle[i]);
				}else{
					cplex.add(cplex.ifThen(cplex.ge(chargeEle[i],0.001),cplex.eq(dischargeEle[i],0)));
				}
				//cplex.addLe(0, totalLoad[i]);
				cplex.addEq(totEVcharge, EVenergy);
			}
			
				//cplex.addEq(availableEleEnergy[23], initEleStore);
				//cplex.addEq(availableHeatEnergy[23], initHeatStore);
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int i=0; i<24; i++){
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					if(name.equals("transport")){
						storageEleProfile[i] = chargeEleProfile[i];
					}else{
						chargeThermProfile[i]=cplex.getValue(chargeTherm[i]);
						dischargeThermProfile[i]=cplex.getValue(dischargeTherm[i]);
						demandProfileHP[i]=(heatDemand[i]+chargeThermProfile[i]-dischargeThermProfile[i])/COP;
						demandEV[i]=cplex.getValue(chargeEV[i]);
						storageEleProfile[i] = chargeEleProfile[i]-dischargeEleProfile[i];
					}
				}
				
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				initHeatStore=cplex.getValue(availableHeatEnergy[23]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Consumer optimisation on price not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void optimiseDemandDFDistrib(
			double maxHeatCap, double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, double maxThermPower, //electrical and thermal power constraints
			double maxHPower, //nominal capacity for the heat pump 
			double effEle, double COP, double effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			double[] demandProfileHP,//charging and discharging profile for the battery
			double[] heatDemand, double[] demandEleBL, 
			double[] genProfile, double aveRes,
			double initEleStore, double initHeatStore, int t1,
			double SOC1, double SOC2, double[] demandEV, double EVcap,
			double maxEVpower, int EVstart, int EVfinish, double effEV, String name){
		try {
			IloCplex cplex = new IloCplex ();
			cplex.setOut(null);
			
			double[] chargeThermProfile = new double[24];
			double[] dischargeThermProfile = new double[24];
			double EVenergy = (SOC2-SOC1)*EVcap;
			
			//determining the number of hours for charging
			int EVlength;
			if(EVfinish>EVstart){
				EVlength=EVfinish-EVstart;
			}else{
				EVlength=EVfinish+24-EVstart;
			}
			
			//decision variables
			IloNumVar[] chargeEV = new IloNumVar[24]; // charge by EV
			IloNumVar[] chargeEle = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			IloNumVar[] chargeTherm = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeTherm = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			
			for (int i=0; i<24; i++){
				if(i<=EVlength){
					chargeEV[i] = cplex.numVar(0, maxEVpower);//EV charge is bound by the EV power constraints
				}else{
					chargeEV[i] = cplex.numVar(0, 0);
				}
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
				chargeTherm[i] = cplex.numVar(0, maxThermPower); //charge P is bound by maxP
				dischargeTherm[i] = cplex.numVar(0, maxThermPower);
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] availableHeatEnergy = new IloNumExpr[24]; //refers to the energy in the thermal storage
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			IloNumExpr[] netHeatEnergy = new IloNumExpr[24]; //net demand for power by thermal store
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total flexible electrical demand by consumer
			IloNumExpr[] demandEleHP = new IloNumExpr[24]; //thermal energy going into thermal storage
			IloNumExpr[] demandThermHP = new IloNumExpr[24]; //thermal energy going into thermal storage
			
			double m=1d/COP;
			
			//electrical HP demand converted to heat demand using COP (either from the battery of from HP)
			for(int i=0; i<24; i++){
				//total thermal demand from HP
				demandThermHP[i]=cplex.sum(cplex.sum(heatDemand[i],chargeTherm[i]),cplex.prod(-1, dischargeTherm[i]));
				//total electricity demand from HP
				demandEleHP[i]=cplex.prod(m, demandThermHP[i]); 
			}
		
			//calculate net charged energy in electrical and thermal storage
			for(int i=0; i<24; i++){
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1,dischargeEle[i])); //net storage for battery
				netHeatEnergy[i]=cplex.sum(chargeTherm[i], cplex.prod(-1,dischargeTherm[i]));//net storage for thermal store
				//total load = charge from EV + demand from HP + demand from electrical storage - discharge of electrical storage
				totalLoad[i]=cplex.sum(chargeEV[i],cplex.sum(demandEleBL[i]-genProfile[i],cplex.sum(demandEleHP[i], cplex.sum(chargeEle[i], cplex.prod(-1,dischargeEle[i]))))); //total flexible load
			}
			
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]);
			availableHeatEnergy[t1] = cplex.sum(initHeatStore, netHeatEnergy[t1]); 
			
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
				availableHeatEnergy[i] = cplex.sum(availableHeatEnergy[k],netHeatEnergy[i]);
			}
			
			double n = 1d/24.0;
			
			IloNumExpr x1 = cplex.prod(totalLoad[0], totalLoad[0]);
			IloNumExpr x2 = cplex.prod(-2*aveRes, totalLoad[0]);
			double x3 = aveRes*aveRes;
			IloNumExpr objective = cplex.sum(x1, cplex.sum(x2, x3)) ;
	
			IloNumExpr objective1; 
			for(int i=1; i<24; i++){
				objective1=objective;
				x1 = cplex.prod(totalLoad[i], totalLoad[i]);
				x2 = cplex.prod(-2*aveRes, totalLoad[i]);
				objective = cplex.sum(x1, cplex.sum(x2, x3), objective1) ;
			}
			objective1=cplex.prod(n, objective);
			
			//define objective
			cplex.addMinimize(objective1);
			
			//CONSTRAINTS
			IloNumExpr totEVcharge = cplex.prod(chargeEV[0],effEV);
			IloNumExpr totEVcharge2;
			for(int i=1; i<24; i++){
				totEVcharge2 = cplex.sum(totEVcharge, cplex.prod(chargeEV[i],effEV));
				totEVcharge = totEVcharge2;
			}
			
			for(int i=0; i<24; i++){
				//HP demand is limited by max and min power constraints
				cplex.addLe(demandEleHP[i],maxHPower);
				cplex.addLe(0,demandEleHP[i]);
				//stored energy cannot exceed storage constraints
				cplex.addLe(availableEleEnergy[i], maxEleCap);
				cplex.addLe(availableHeatEnergy[i], maxHeatCap);
				cplex.addLe(0, availableEleEnergy[i]);
				cplex.addLe(0, availableHeatEnergy[i]);
				cplex.addLe(0, totalLoad[i]);
				cplex.addEq(totEVcharge, EVenergy);
				if(name.equals("transport")){
					cplex.addEq(dischargeEleProfile[i],dischargeEle[i]);
				}else{
					cplex.add(cplex.ifThen(cplex.ge(chargeEle[i],0.001),cplex.eq(dischargeEle[i],0)));
				}
			}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int i=0; i<24; i++){
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					if(name.equals("transport")){
						storageEleProfile[i] = chargeEleProfile[i];
					}else{
						dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
						chargeThermProfile[i]=cplex.getValue(chargeTherm[i]);
						dischargeThermProfile[i]=cplex.getValue(dischargeTherm[i]);
						demandProfileHP[i]=(heatDemand[i]+chargeThermProfile[i]-dischargeThermProfile[i])/COP;
						demandEV[i]=cplex.getValue(chargeEV[i]);
						storageEleProfile[i] = chargeEleProfile[i]-dischargeEleProfile[i];
					}
				}
				
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				initHeatStore=cplex.getValue(availableHeatEnergy[23]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void readDoubleData(String doc, double[] array, int size) {
		try(Scanner file = new Scanner(new FileReader(doc)))
		{
			for(int i=0; i<size; i++){
				array[i]=file.nextDouble();							
			}
		}
		catch (Exception e) {
		    System.err.println("Exception present" + e);
		}//System.out.println(Arrays.toString(array));
	}

	public static void readData(String doc, double[][] array, double multiplier, int x, int y) {
		try(Scanner file = new Scanner(new FileReader(doc)))
		{
			for(int i=0; i<x; i++){
				for(int j=0; j<y; j++){
					array[i][j]=file.nextDouble() * multiplier;							
				}
			}
			file.close();
		}
		catch (Exception e) {
		    System.err.println("Exception present" + e);
		}//System.out.println(Arrays.toString(array));
	}
	
	public static void readData(String doc, int[][][] array, int sector, int x, int y) {
		try(Scanner file = new Scanner(new FileReader(doc)))
		{
			for(int i=0; i<x; i++){
				for(int j=0; j<y; j++){
					array[i][j][sector]=file.nextInt();							
				}
			}
			file.close();
		}
		catch (Exception e) {
		    System.err.println("Exception present" + e);
		}//System.out.println(Arrays.toString(array));
	}
	
	public static void readData(String doc, double[][][] array, int sector, int x, int y) {
		try(Scanner file = new Scanner(new FileReader(doc)))
		{
			for(int i=0; i<x; i++){
				for(int j=0; j<y; j++){
					array[i][j][sector]=file.nextDouble();							
				}
			}
			file.close();
		}
		catch (Exception e) {
		    System.err.println("Exception present" + e);
		}//System.out.println(Arrays.toString(array));
	}
	
	public static void readIntData(String doc, int[][] array, int multiplier, int x, int y) {
		try(Scanner file = new Scanner(new FileReader(doc)))
		{
			for(int i=0; i<x; i++){
				for(int j=0; j<y; j++){
					array[i][j]=file.nextInt() * multiplier;							
				}
			}
			file.close();
		}
		catch (Exception e) {
		    System.err.println("Exception present" + e);
		}//System.out.println(Arrays.toString(array));
	}
	
	public static void zero(double[] array) {
		for(int i=0; i<24; i++){
			array[i] = 0;
		}
	}
	
	public static void writeArrayToFile(File file, String string, double[] profile) {
		String formatedString = Arrays.toString(profile)
			    .replace("[", "")  //remove the right bracket
			    .replace("]", "")  //remove the left bracket
			    .trim();
		try{	
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        bw.newLine();
	        bw.write(string + formatedString);
	        bw.close();
	}catch(IOException e)
    	{
			System.out.println(e);
    	}
	}
	
	//the right write method
	public static void writeToFile(File file, String string, double[] profile) throws IOException {
		try{
        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
        	for(int i=0; i<24; i++){
        		bw.newLine();
        		bw.write(string + i + "," + profile[i]);
        	}
        	bw.close();
		}catch(IOException e)
    	{
			System.out.println(e);
    	}
	}
	
	//the right write method
		public static void writeToFile(File file, String string, double[] profile1, double[] profile2) throws IOException {
			try{
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        	for(int i=0; i<24; i++){
	        		bw.newLine();
	        		bw.write(string + i + "," + profile1[i]+ "," + profile2[i]);
	        	}
	        	bw.close();
			}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
		}
		
		public static void writeToFile(File file, String string, double[][] profile1, double[] profile2) throws IOException {
			try{
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        	for(int i=0; i<24; i++){
	        		bw.newLine();
	        		bw.write(string + i + "," + profile1[i][0]+ ","+ profile1[i][1]+ ","+ profile1[i][2]+ ","+ profile1[i][3]+ ","
	        				+ profile1[i][4]+ "," + profile1[i][5]+ ","+ profile1[i][6]+ ","+ profile1[i][7]+ ","+ profile1[i][8]+ ","
	        				+ profile1[i][9]+ "," + profile2[i]);
	        	}
	        	bw.close();
			}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
		}
		
		public static void writeToFile(File file, String string, double[][] profile1) throws IOException {
			try{
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        	for(int i=0; i<24; i++){
	        		bw.newLine();
	        		bw.write(string + i + "," + profile1[i][0]+ ","+ profile1[i][1]+ ","+ profile1[i][2]+ ","+ profile1[i][3]+ ","
	        				+ profile1[i][4]+ "," + profile1[i][5]+ ","+ profile1[i][6]+ ","+ profile1[i][7]+ ","+ profile1[i][8]+ ","
	        				+ profile1[i][9]);
	        	}
	        	bw.close();
			}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
		}
		
		public static void writeToFile(File file, String string, double[][] profile1, double[] profile2, double[] profile3, double[] profile4, double[] profile5, double trade) throws IOException {
			try{
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        	for(int i=0; i<24; i++){
	        		bw.write(string + i + "," + profile1[i][0]+ ","+ profile1[i][1]+ ","+ profile1[i][2]+ ","+ profile1[i][3]+ ","
	        				+ profile1[i][4]+ "," + profile1[i][5]+ ","+ profile1[i][6]+ ","+ profile1[i][7]+ ","+ profile1[i][8]+ ","
	        				+ profile1[i][9]+ "," + profile2[i] + "," + profile3[i]+ "," + profile4[i]+ "," + profile5[i] + "," + trade);
	        		bw.newLine();
	        	}
	        	bw.close();
			}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
		}
		
		public static void writeToFile(File file, String string, double[] profile1, double[] profile2, double[] profile3, 
				double[] profile4, double[] profile5, double x) throws IOException {
			try{
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        	for(int i=0; i<24; i++){
	        		bw.write(string +"," + i + "," + profile1[i]+ "," + profile2[i]+ "," + profile3[i] + 
	        				"," + profile4[i] + "," + profile5[i] + "," + -x);
	        		bw.newLine();
	        	}
	        	bw.close();
			}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
		}
		
		public static void writeToFile(File file, String string, double[] profile1, double[] profile2, double[] profile3, 
				double[] profile4) throws IOException {
			try{
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        	for(int i=0; i<24; i++){
	        		bw.write(string +"," + i + "," + profile1[i]+ "," + profile2[i]+ "," + profile3[i] + 
	        				"," + profile4[i]);
	        		bw.newLine();
	        	}
	        	bw.close();
			}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
		}
		
		public static void writeToFile(File file, String string, double[] profile1, double[] profile2, double[] profile3, 
				double[] profile4, double[] profile5, double[] profile6) throws IOException {
			try{
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        	for(int i=0; i<24; i++){
	        		bw.newLine();
	        		bw.write(string + i + "," + profile1[i]+ "," + profile2[i]+ "," + profile3[i] + "," + profile4[i]+ "," + profile5[i]+ "," + profile6[i]);
	        	}
	        	bw.close();
			}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
		}
	
	public static void writeToFile(File file, String string, double value) throws IOException {
		try{
        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
        	bw.newLine();
        	bw.write(string + value);
        	bw.close();
		}catch(IOException e)
    	{
			System.out.println(e);
    	}
	}
	
	public static void writeToFileConsumerData(File demandFile, File usedRESFile, File curtRESFile, 
			File supFile, File hpFile, File residualFile, File multiplierFile,
			String titles, ArrayList<Consumer24> consumers){
		try{
			BufferedWriter bw1 = new BufferedWriter(new FileWriter(demandFile, true));
			BufferedWriter bw2 = new BufferedWriter(new FileWriter(usedRESFile, true));
			BufferedWriter bw3 = new BufferedWriter(new FileWriter(curtRESFile, true));
			BufferedWriter bw4 = new BufferedWriter(new FileWriter(supFile, true));
			BufferedWriter bw5 = new BufferedWriter(new FileWriter(hpFile, true));
			BufferedWriter bw6 = new BufferedWriter(new FileWriter(residualFile, true));
			BufferedWriter bw7 = new BufferedWriter(new FileWriter(multiplierFile, true));
		       	for(int i=0; i<24; i++){
		       		bw1.write(titles + "," + i);
		       		bw2.write(titles + "," + i);
		       		bw3.write(titles + "," + i);
		       		bw5.write(titles + "," + i);
		       		bw6.write(titles + "," + i);
		       		bw7.write(titles + "," + i);
		       		
		       		for(final Consumer24 con : consumers){
		       			bw1.write("," + con.getDemand()[i]); 
		       			bw2.write("," + con.getUsedRES()[i]);
		       			bw3.write("," + con.getCurtRES()[i]);
		       			bw5.write("," + con.getThermalDemand()[i]);
		       			bw6.write("," + con.getResidual()[i]);
		       			bw7.write("," + con.getConMultiplier());
		       		}
		       		bw1.newLine();
		       		bw2.newLine();
		       		bw3.newLine();
		       		bw5.newLine();
		       		bw6.newLine();
		       		bw7.newLine();
		       	}
		       	bw1.close();
		       	bw2.close();
		       	bw3.close();
		       	bw5.close();
		       	bw6.close();
		       	bw7.close();
		       	
	       		bw4.write(titles);
		       	for(final Consumer24 con : consumers){
		       		bw4.write("," + con.getSup());
		       	}
		       	bw4.newLine();
		       	bw4.close();
			}catch(IOException e)
		    {
				System.out.println(e);
		    }
	}
	
	public static void writeToFileAggData(File demandFile, File tariffFile, String titles, ArrayList<Aggregator24> aggregators){
		try{
			BufferedWriter bw1 = new BufferedWriter(new FileWriter(demandFile, true));
			BufferedWriter bw2 = new BufferedWriter(new FileWriter(tariffFile, true));
		       	for(int i=0; i<24; i++){
		       		bw1.write(titles + "," + i);
		       		for(final Aggregator24 agg : aggregators){
		       			bw1.write("," + agg.getAggDemand()[i]);
		       		}
		       		bw1.newLine();
		       	}
		       	bw1.close();
		  
	       		bw2.write(titles);
	       		for(final Aggregator24 agg : aggregators){
	       			bw2.write("," + agg.getTariff()); 
	       		}
	       		bw2.newLine();
	       		bw2.close();
			}catch(IOException e)
		    {
				System.out.println(e);
		    }
	}

	static void writeToFile3(File file, int k, double[] profile) {
		try{	
        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
        bw.write(k + Arrays.toString(profile));
        bw.newLine();
        bw.close();
 
	}catch(IOException e)
    	{
			System.out.println(e);
    	}
	}
	
	static void writeToFile2(File file, int y, int d, double[] profile) {
		try{	
        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
        bw.write(y +"," + d + Arrays.toString(profile));
        bw.newLine();
        bw.close();
 
	}catch(IOException e)
    	{
			System.out.println(e);
    	}
	}

	public static void writeToFile(File file, String string) {
		try{	
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        bw.write(string);
	        bw.newLine();
	        bw.close();
	 
		}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
	}

	public static double[] readDoubleData(String doc, int length) {
		double[] array = new double[length];
		try(Scanner file = new Scanner(new FileReader(doc)))
		{
			for(int i=0; i<length; i++){
					array[i]=file.nextDouble();							
			}
		}
		catch (Exception e) {
		    System.err.println("Exception present" + e);
		}
		return array;
	}
	

	public static int[] readIntData2(String doc, int length) {
		int[] array = new int[length];
		try(Scanner file = new Scanner(new FileReader(doc)))
		{
			for(int i=0; i<length; i++){
					array[i]=file.nextInt();							
			}
		}
		catch (Exception e) {
		    System.err.println("Exception present" + e);
		}
		return array;
	}
	

	
	static void writeToFile(String str, File file, double[] array) {
		try{
			fw=new FileWriter(str +".txt", true);
	        BufferedWriter bw = new BufferedWriter(fw);
	        
	        try
	        {
	            fr=new FileReader(str +".txt");
	            br=new BufferedReader(fr);
	            
	            while((s=br.readLine())!=null)
	            {
	                strtok=new StringTokenizer(s," ");
	                while(strtok.hasMoreTokens())
	                {
	                	for(int i=0; i<24; i++){
	        	        	bw.write(Double.toString(array[i]));
	        	        	bw.newLine();
	        	        }
	                }

	            }
	                br.close();
	        }
	        catch(FileNotFoundException e)
	        {
	            System.out.println("File was not found!");
	        }
	        catch(IOException e)    
	        {
	            System.out.println("No file found!");
	        }
	        bw.close();
	    }
	    catch(FileNotFoundException e)
	    {
	        System.out.println("Error1!");
	    }
	    catch(IOException e)    
	    {
	        System.out.println("Error2!");
	    }    
}
  

	public static void addNextLine(File file) {
		try{	
	        BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
	        bw.newLine();
	        bw.close();
	 
		}catch(IOException e)
	    	{
				System.out.println(e);
	    	}
	}

	//demand flattening for non-thermal demand
	public static void flattenDemandNonThermDistrib(double maxCap, double maxP, 
			double eff, double[] chargeProfile, double[] dischargeProfile, double[] storageProfile,
			int t1, double[] demandEleBL, double[] genProfile,
			double initEleStore){
		try {
			IloCplex cplex = new IloCplex ();
			
			IloNumExpr aveRes, totRes;
			int t2=(t1+23)%24; //last time period of the optimisation array
			
			//variables
			IloNumVar[] charge = new IloNumVar[24];
			IloNumVar[] discharge = new IloNumVar[24];
			
			for (int i=0; i<24; i++){
				charge[i] = cplex.numVar(0, maxP); //charge P is bound by maxP
				discharge[i] = cplex.numVar(0, maxP); 
			}
			
			//expressions
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //electrical energy available in the battery
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net electrical power going into the battery (after losses)
			IloNumExpr[] totalLoad = new IloNumExpr[24];
			
			//calculate net charged energy in a battery
			for(int j=t1; j<24+t1; j++){
				int i=j%24;
				netEleEnergy[i]=cplex.sum(cplex.prod(eff,charge[i]), cplex.prod(-1.0, discharge[i])); //net energy going into the battery (after eff losses)
				totalLoad[i]=cplex.sum(cplex.sum(charge[i], cplex.prod(-eff,discharge[i])), demandEleBL[i]-genProfile[i]); //total flexible load (after losses on discharge of battery)
			}
			
			totRes=totalLoad[0];
			
			double m=1d/24;
			for(int i=1; i<24; i++){
				totRes=cplex.sum(totalLoad[i], totRes);
			}
			aveRes=cplex.prod(totRes, m);
			
			IloNumExpr x1 = cplex.prod(totalLoad[0], totalLoad[0]);
			IloNumExpr x2 = cplex.prod(cplex.prod(-2, aveRes), totalLoad[0]);
			IloNumExpr x3 = cplex.prod(aveRes, aveRes);
			IloNumExpr objective = cplex.sum(x1, cplex.sum(x2, x3)) ;
	
			IloNumExpr objective1; 
			for(int i=1; i<24; i++){
				objective1=objective;
				x1 = cplex.prod(totalLoad[i], totalLoad[i]);
				x2 = cplex.prod(cplex.prod(-2, aveRes), totalLoad[i]);
				x3 = cplex.prod(aveRes, aveRes);
				objective = cplex.sum(x1, cplex.sum(x2, x3), objective1) ;
			}
			objective1=cplex.sum(m, objective);
			
			//define objective
			cplex.addMinimize(objective1);
			
			//initial amount of energy in the thermal and electrical stores passed from previous day
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]); //0.9*maxEleCap = initial amount of energy in a store
			
			for(int j=t1+1; j<24+t1; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
			}
			
			//C1:discharge in the first time period cannot be bigger than the initial energy
			cplex.addLe(discharge[t1], initEleStore);
			//C2:energy at the end of the day in the battery must be the same as at the beginning
			cplex.addEq(availableEleEnergy[t2],initEleStore);
			//C3: available energy in the battery cannot be bigger than the battery capacity
			cplex.addLe(availableEleEnergy[t1], maxCap);
			//C4: time limit of charg and discharge in one time period
			cplex.addLe(cplex.sum(charge[0], discharge[0]), maxP);
			
			//C5+
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
					cplex.addLe(discharge[i], availableEleEnergy[(i+23)%24]);
					cplex.addLe(availableEleEnergy[i], maxCap);//C4
					cplex.addLe(0, availableEleEnergy[i]);//available energy cannot be negative in the battery
					cplex.addLe(cplex.sum(charge[i], discharge[i]), maxP); //C6
				}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int i=0; i<24; i++){
					chargeProfile[i]=cplex.getValue(charge[i]);
					dischargeProfile[i]=cplex.getValue(discharge[i]);
					storageProfile[i] = chargeProfile[i]-eff*dischargeProfile[i]; //discharge is reduced by losses
				}
				initEleStore=cplex.getValue(availableEleEnergy[t2]);
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void flattenDemandWithThermDistrib(double maxHeatCap, double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, //electrical storage power capacity
			double maxHPower, //nominal capacity for the heat pump 
			double effEle, double COP, double effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			int t1, //start time slot for optimisation
			double[] demandProfileHP, //array to fill with the outcome of optimisation
			double[] heatDemand, double[] demandEleBL, //consumer thermal and electricity demand
			double[] genProfile,
			double initEleStore, double initHeatStore){
		try {
			IloCplex cplex = new IloCplex ();
			//cplex.setOut(null);
			
			IloNumExpr aveRes, totRes;
			int t2=(t1+23)%24; //last time period of the optimisation array
			
			double[] dischargeTherm = new double[24];
			
			//variables
			IloNumVar[] chargeEle = new IloNumVar[24]; //DV1 - charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //DV2 - discharge by an electrical store
			IloNumVar[] demandHP = new IloNumVar[24]; //DV3 - demand by the heat pump
			
			for (int i=0; i<24; i++){
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
				demandHP[i] = cplex.numVar(0, maxHPower);//power input by HP is constrained by the rated capacity
				dischargeTherm[i] = heatDemand[i]/effTherm;//thermal energy leaving thermal store
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //electrical energy available in the battery
			IloNumExpr[] availableHeatEnergy = new IloNumExpr[24]; //heat available in the therm store
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net electrical power going into the battery (after losses)
			IloNumExpr[] netHeatEnergy = new IloNumExpr[24]; //net heat energy going into the thermal store (after losses)
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total demand from the grid by consumer
			IloNumExpr[] chargeTherm = new IloNumExpr[24]; //thermal energy going into thermal store
			
			//electrical HP demand converted to heat demand going into heat store (before losses)
			for(int i=0; i<24; i++){
				chargeTherm[i]=cplex.prod(COP, demandHP[i]); 
			}
		
			//calculate net charged energy in a battery
			for(int j=t1; j<24+t1; j++){
				int i=j%24;
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1.0, dischargeEle[i])); //net energy going into the battery (after eff losses)
				netHeatEnergy[i]=cplex.sum(cplex.prod(effTherm,chargeTherm[i]), -1.0 *dischargeTherm[i]);//net energy going into thermal store (after eff losses)
				totalLoad[i]=cplex.sum(cplex.sum(demandHP[i], cplex.sum(chargeEle[i], cplex.prod(-effEle,dischargeEle[i]))), demandEleBL[i]-genProfile[i]); //total flexible load (after losses on discharge of battery)
			}
		
			totRes=totalLoad[0];
			
			double m=1d/24;
			for(int i=1; i<24; i++){
				totRes=cplex.sum(totalLoad[i], totRes);
			}
			aveRes=cplex.prod(totRes, m);
			
			IloNumExpr x1 = cplex.prod(totalLoad[0], totalLoad[0]);
			IloNumExpr x2 = cplex.prod(cplex.prod(-2, aveRes), totalLoad[0]);
			IloNumExpr x3 = cplex.prod(aveRes, aveRes);
			IloNumExpr objective = cplex.sum(x1, cplex.sum(x2, x3)) ;
	
			IloNumExpr objective1; 
			for(int i=1; i<24; i++){
				objective1=objective;
				x1 = cplex.prod(totalLoad[i], totalLoad[i]);
				x2 = cplex.prod(cplex.prod(-2, aveRes), totalLoad[i]);
				x3 = cplex.prod(aveRes, aveRes);
				objective = cplex.sum(x1, cplex.sum(x2, x3), objective1);
			}
			
			objective1=cplex.prod(objective, m);
			
			//Objective function = total cost of electricity
			cplex.addMinimize(objective1);
			
			//CONSTRAINTS
			
			//initial amount of energy in the thermal and electrical stores passed from previous day
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]); //0.9*maxEleCap = initial amount of energy in a store
			availableHeatEnergy[t1] = cplex.sum(initHeatStore, netHeatEnergy[t1]); //0.9*maxHeatCap = initial amount of energy in a store
			
			for(int j=t1+1; j<24+t1; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
				availableHeatEnergy[i] = cplex.sum(availableHeatEnergy[k],netHeatEnergy[i]);
			}
			
			//C1
			cplex.addLe(dischargeEle[t1], initEleStore);
			//C2
			cplex.addEq(availableEleEnergy[t2],initEleStore);
			//C3
			cplex.addEq(availableHeatEnergy[t2],initHeatStore);
			//C4
			cplex.addLe(availableEleEnergy[t1], maxEleCap);
			//C5
			cplex.addLe(availableHeatEnergy[t1], maxHeatCap);
			cplex.addLe(0, availableEleEnergy[t1]);
			cplex.addLe(0, availableHeatEnergy[t1]);
			//C6
			cplex.addLe(cplex.sum(chargeEle[0], dischargeEle[0]), maxElePower);
			
			//C7+
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
					cplex.addLe(dischargeEle[i], availableEleEnergy[(i+23)%24]);
					cplex.addLe(availableEleEnergy[i], maxEleCap);
					cplex.addLe(availableHeatEnergy[i], maxHeatCap);
					cplex.addLe(0, availableEleEnergy[i]);
					cplex.addLe(0, availableHeatEnergy[i]);
					cplex.addLe(cplex.sum(chargeEle[i], dischargeEle[i]), maxElePower);
				}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int j=t1; j<t1+24; j++){
					int i=j%24;
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					storageEleProfile[i] = chargeEleProfile[i]-effEle * dischargeEleProfile[i]; //discharge is reduced by losses
					demandProfileHP[i]=cplex.getValue(demandHP[i]);
				}
				initEleStore=cplex.getValue(availableEleEnergy[t2]);
				initHeatStore=cplex.getValue(availableHeatEnergy[t2]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void flattenDemandWithThermEVDistrib(double maxHeatCap, double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, //electrical storage power capacity
			double maxHPower, //nominal capacity for the heat pump 
			double effEle, double COP, double effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			int t1, //start time slot for optimisation
			double[] demandProfileHP, //array to fill with the outcome of optimisation
			double[] heatDemand, double[] demandEleBL, //consumer thermal and electricity demand
			double[] genProfile,
			double initEleStore, double initHeatStore,
			double SOC1, double SOC2, double[] demandEV, double EVcap,
			double maxEVpower, int EVstart, int EVfinish, double effEV){
		try {
			IloCplex cplex = new IloCplex ();
			
			//determining the number of hours for charging
			int EVlength;
			if(EVfinish>EVstart){
				EVlength=EVfinish-EVstart;
			}else{
				EVlength=EVfinish+24-EVstart;
			}
			
			IloNumExpr aveRes, totRes;
			int t2=(t1+23)%24; //last time period of the optimisation array
			
			double[] dischargeTherm = new double[24];
			
			//variables
			IloNumVar[] chargeEle = new IloNumVar[24]; //DV1 - charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //DV2 - discharge by an electrical store
			IloNumVar[] demandHP = new IloNumVar[24]; //DV3 - demand by the heat pump
			IloNumVar[] chargeEV = new IloNumVar[EVlength]; //demand by the heat pump independently
			
			for (int i=0; i<24; i++){
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
				demandHP[i] = cplex.numVar(0, maxHPower);//power input by HP is constrained by the rated capacity
				dischargeTherm[i] = heatDemand[i]/effTherm;
			}
			
			for (int i=0; i<EVlength; i++){
				chargeEV[i] = cplex.numVar(0, maxEVpower);
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //electrical energy available in the battery
			IloNumExpr[] availableHeatEnergy = new IloNumExpr[24]; //heat available in the therm store
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net electrical power going into the battery (after losses)
			IloNumExpr[] netHeatEnergy = new IloNumExpr[24]; //net heat energy going into the thermal store (after losses)
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total demand from the grid by consumer
			IloNumExpr[] chargeTherm = new IloNumExpr[24]; //power going into thermal store
			IloNumExpr[] availableEVEnergy = new IloNumExpr[EVlength]; //refers to the energy in the EV
			
			//electrical HP demand converted to heat demand going into heat store (before losses)
			for(int i=0; i<24; i++){
				chargeTherm[i]=cplex.prod(COP, demandHP[i]); 
			}
		
			//calculate net charged energy in a battery
			for(int j=t1; j<24+t1; j++){
				int i=j%24;
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1.0, dischargeEle[i])); //net energy going into the battery (after eff losses)
				netHeatEnergy[i]=cplex.sum(cplex.prod(effTherm,chargeTherm[i]), -1.0 *dischargeTherm[i]);//net energy going into thermal store (after eff losses)
				totalLoad[i]=cplex.sum(cplex.sum(demandHP[i], cplex.sum(chargeEle[i], cplex.prod(-effEle,dischargeEle[i]))), demandEleBL[i]-genProfile[i]); //total flexible load (after losses on discharge of battery)
			}
			
			for(int i=0; i<EVlength; i++){
				int j=(EVstart+i)%24;
				totalLoad[j]=cplex.sum(chargeEV[i], totalLoad[j]);
			}
		
			totRes=totalLoad[0];
			
			double m=1d/24;
			for(int i=1; i<24; i++){
				totRes=cplex.sum(totalLoad[i], totRes);
			}
			aveRes=cplex.prod(totRes, m);
			
			IloNumExpr x1 = cplex.prod(totalLoad[0], totalLoad[0]);
			IloNumExpr x2 = cplex.prod(cplex.prod(-2, aveRes), totalLoad[0]);
			IloNumExpr x3 = cplex.prod(aveRes, aveRes);
			IloNumExpr objective = cplex.sum(x1, cplex.sum(x2, x3)) ;
	
			IloNumExpr objective1; 
			for(int i=1; i<24; i++){
				objective1=objective;
				x1 = cplex.prod(totalLoad[i], totalLoad[i]);
				x2 = cplex.prod(cplex.prod(-2, aveRes), totalLoad[i]);
				x3 = cplex.prod(aveRes, aveRes);
				objective = cplex.sum(x1, cplex.sum(x2, x3), objective1);
			}
			
			objective1=cplex.prod(objective, m);
			
			//Objective function = total cost of electricity
			cplex.addMinimize(objective1);
			
			//initial amount of energy in the thermal and electrical stores passed from previous day
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]); //0.9*maxEleCap = initial amount of energy in a store
			availableHeatEnergy[t1] = cplex.sum(initHeatStore, netHeatEnergy[t1]); //0.9*maxHeatCap = initial amount of energy in a store
			availableEVEnergy[t1] = cplex.sum(SOC1 * EVcap, cplex.prod(chargeEV[t1],effEV));
			
			for(int j=t1+1; j<24+t1; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
				availableHeatEnergy[i] = cplex.sum(availableHeatEnergy[k],netHeatEnergy[i]);
			}
			
			for(int i=1; i<EVlength; i++){
				availableEVEnergy[i] = cplex.sum(availableEVEnergy[i-1],cplex.prod(chargeEV[i],effEV));
			}
			
			//C1:discharge in the first time period cannot be bigger than the initial energy
			cplex.addLe(dischargeEle[t1], initEleStore);
			//C1:energy at the end of the day in the battery must be the same as at the beginning
			cplex.addEq(availableEleEnergy[t2],initEleStore);
			//C2:energy at the end of the day in the thermal store must be the same as at the beginning
			cplex.addEq(availableHeatEnergy[t2],initHeatStore);
			//C3: available energy in the battery cannot be bigger than the battery capacity
			cplex.addLe(availableEleEnergy[t1], maxEleCap);
			//C4: available energy in the TES cannot be bigger than the TES capacity
			cplex.addLe(availableHeatEnergy[t1], maxHeatCap);
			//C5: time limit of charge and discharge in one time period
			cplex.addLe(cplex.sum(chargeEle[0], dischargeEle[0]), maxElePower);
			//C6
			cplex.addEq(availableEVEnergy[EVlength-1], EVcap*SOC2);
			
			//C7-151
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
					cplex.addLe(dischargeEle[i], availableEleEnergy[(i+23)%24]);
					cplex.addLe(availableEleEnergy[i], maxEleCap);
					cplex.addLe(availableHeatEnergy[i], maxHeatCap);
					cplex.addLe(0, availableEleEnergy[i]);//available energy cannot be negative in the battery
					cplex.addLe(0, availableHeatEnergy[i]);//available energy cannot be negative in TES
					cplex.addLe(cplex.sum(chargeEle[i], dischargeEle[i]), maxElePower);
				}
			//C152+
			for(int i=0; i<EVlength; i++){
				cplex.addLe(availableEVEnergy[i], EVcap);
			}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int j=t1; j<t1+24; j++){
					int i=j%24;
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					storageEleProfile[i] = chargeEleProfile[i]-effEle * dischargeEleProfile[i]; //discharge is reduced by losses
					demandProfileHP[i]=cplex.getValue(demandHP[i]);
				}
				
				for(int i=0; i<EVlength; i++){
					int j=(EVstart+i)%24;
					demandEV[j]=cplex.getValue(chargeEV[i]);
				}
				initEleStore=cplex.getValue(availableEleEnergy[t2]);
				initHeatStore=cplex.getValue(availableHeatEnergy[t2]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	//method to coordinate consumer demand based on a signal
	public static void flattenDemandWithThermOnCentral(double maxHeatCap, double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, //electrical storage power capacity
			double maxHPower, //nominal capacity for the heat pump 
			double effEle, double COP, double effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			int t1, //start time slot for optimisation
			double[] demandProfileHP,//charging and discharging profile for the battery
			double[] heatDemand, double[] demandEleBL, double[] flexDemand0,
			double[] genProfile,
			double initEleStore, double initHeatStore, double LR){
		try {
			IloCplex cplex = new IloCplex ();
			
			int t2=(t1+23)%24; //last time period of the optimisation array
			
			//decision variables
			IloNumVar[] chargeEle = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			IloNumVar[] demandHP = new IloNumVar[24]; //demand by the heat pump independently
			
			for (int i=0; i<24; i++){
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
				demandHP[i] = cplex.numVar(0, maxHPower);//power input by HP is constrained by the rated capacity
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to available energy in the battery
			IloNumExpr[] availableHeatEnergy = new IloNumExpr[24]; //refers to available energy in the thermal storage
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			IloNumExpr[] netHeatEnergy = new IloNumExpr[24]; //net demand for power by thermal store
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total flexible electrical demand by consumer
			IloNumExpr[] chargeTherm = new IloNumExpr[24]; //thermal energy going into thermal storage
						
			//electrical HP demand converted to heat demand using COP (either from the battery of from HP)
			for(int i=0; i<24; i++){
				chargeTherm[i]=cplex.prod(COP, demandHP[i]); 
			}
		
			//calculate net charged energy in electrical and thermal storage
			for(int j=t1; j<24+t1; j++){
				int i=j%24;
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1,dischargeEle[i])); //net power going into the battery
				netHeatEnergy[i]=cplex.sum(cplex.prod(effTherm,chargeTherm[i]), -1.0 *heatDemand[i]);//net heat going into the thermal store
				totalLoad[i]=cplex.sum(demandHP[i], cplex.sum(chargeEle[i], cplex.prod(-effEle,dischargeEle[i]))); //total flexible load experienced by the consumer
			}
			
			IloNumExpr x1 = cplex.prod(signal[0], totalLoad[0]);
			IloNumExpr x2 = cplex.prod(totalLoad[0], totalLoad[0]);
			IloNumExpr x3 = cplex.prod(LR, x2);
			IloNumExpr x4 = cplex.prod(-2*LR, cplex.prod(flexDemand0[0], totalLoad[0]));
			double x5 = LR * flexDemand0[0] * flexDemand0[0];
			IloNumExpr objective = cplex.sum(x5, cplex.sum(x1, x3, x4)) ;
	
			for(int i=1; i<24; i++){
				IloNumExpr objective1 = objective;
				x1 = cplex.prod(signal[i], totalLoad[i]);
				x2 = cplex.prod(totalLoad[i], totalLoad[i]);
				x3 = cplex.prod(LR, x2);
				x4 = cplex.prod(-2*LR, cplex.prod(flexDemand0[i], totalLoad[i]));
				x5 = LR * flexDemand0[i] * flexDemand0[i];
				objective = cplex.sum(cplex.sum(x5, cplex.sum(x1, x3, x4)), objective1) ;
			}
			
			//define objective
			cplex.addMinimize(objective);
			
			//CONSTRAINTS
			
			//initial amount of energy in the thermal and electrical stores passed from previous day
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]); //0.9*maxEleCap = initial amount of energy in a store
			availableHeatEnergy[t1] = cplex.sum(initHeatStore, netHeatEnergy[t1]); //0.9*maxHeatCap = initial amount of energy in a store
			
			for(int j=t1+1; j<24+t1; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
				availableHeatEnergy[i] = cplex.sum(availableHeatEnergy[k],netHeatEnergy[i]);
			}
			
			//C1
			cplex.addLe(dischargeEle[t1], initEleStore);
			//C2
			cplex.addEq(availableEleEnergy[t2],initEleStore);
			//C3
			cplex.addEq(availableHeatEnergy[t2],initHeatStore);
			//C4
			cplex.addLe(availableEleEnergy[t1], maxEleCap);
			//C5
			cplex.addLe(availableHeatEnergy[t1], maxHeatCap);
			//C6
			cplex.addLe(cplex.sum(chargeEle[0], dischargeEle[0]), maxElePower);
			
			//C7+
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
					cplex.addLe(dischargeEle[i], availableEleEnergy[(i+23)%24]);
					cplex.addLe(availableEleEnergy[i], maxEleCap);
					cplex.addLe(availableHeatEnergy[i], maxHeatCap);
					cplex.addLe(0, availableEleEnergy[i]);
					cplex.addLe(0, availableHeatEnergy[i]);
					cplex.addLe(cplex.sum(chargeEle[i], dischargeEle[i]), maxElePower);
				}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int j=t1; j<t1+24; j++){
					int i=j%24;
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					storageEleProfile[i] = chargeEleProfile[i]-dischargeEleProfile[i];
					demandProfileHP[i]=cplex.getValue(demandHP[i]);
				}
				initEleStore=cplex.getValue(availableEleEnergy[t2]);
				initHeatStore=cplex.getValue(availableHeatEnergy[t2]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void flattenDemandWithThermWithEVOnCentral(double maxHeatCap, double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, //electrical storage power capacity
			double maxHPower, //nominal capacity for the heat pump 
			double effEle, double COP, double effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			int t1, //start time slot for optimisation
			double[] demandProfileHP,//charging and discharging profile for the battery
			double[] heatDemand, double[] demandEleBL, double[] flexDemand0,
			double[] genProfile,
			double initEleStore, double initHeatStore,
			double SOC1, double SOC2, double[] demandEV, double EVcap, 
			double maxEVpower, int EVstart, int EVfinish, double effEV, double LR){
		try {
			IloCplex cplex = new IloCplex ();
			
			//determining the number of hours for charging
			int EVlength;
			if(EVfinish>EVstart){
				EVlength=EVfinish-EVstart;
			}else{
				EVlength=EVfinish+24-EVstart;
			}
			
			int t2=(t1+23)%24; //last time period of the optimisation array
			
			//decision variables
			IloNumVar[] chargeEle = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			IloNumVar[] demandHP = new IloNumVar[24]; //demand by the heat pump independently
			IloNumVar[] chargeEV = new IloNumVar[EVlength]; //demand by the heat pump independently
			
			for (int i=0; i<24; i++){
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
				demandHP[i] = cplex.numVar(0, maxHPower);//power input by HP is constrained by the rated capacity
			}
			
			for (int i=0; i<EVlength; i++){
				chargeEV[i] = cplex.numVar(0, maxEVpower);
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] availableHeatEnergy = new IloNumExpr[24]; //refers to the energy in the thermal storage
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			IloNumExpr[] netHeatEnergy = new IloNumExpr[24]; //net demand for power by thermal store
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total flexible electrical demand by consumer
			IloNumExpr[] chargeTherm = new IloNumExpr[24]; //thermal energy going into thermal storage
			IloNumExpr[] availableEVEnergy = new IloNumExpr[EVlength]; //refers to the energy in the EV
						
			//electrical HP demand converted to heat demand using COP (either from the battery of from HP)
			for(int i=0; i<24; i++){
				chargeTherm[i]=cplex.prod(COP, demandHP[i]); 
			}
		
			//calculate net charged energy in electrical and thermal storage
			for(int j=t1; j<24+t1; j++){
				int i=j%24;
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-1,dischargeEle[i])); //net storage for battery
				netHeatEnergy[i]=cplex.sum(cplex.prod(effTherm,chargeTherm[i]), -1.0 *heatDemand[i]);//net storage for thermal store
				//total load = charge from EV + demand from HP + demand from electrical storage - discharge of electrical storage
				totalLoad[i]=cplex.sum(demandHP[i], cplex.sum(chargeEle[i], cplex.prod(-effEle,dischargeEle[i]))); //total flexible load
			}
			
			for(int i=0; i<EVlength; i++){
				int j=(EVstart+i)%24;
				totalLoad[j]=cplex.sum(chargeEV[i], totalLoad[j]);
			}
			
			IloNumExpr x1 = cplex.prod(signal[0], totalLoad[0]);
			IloNumExpr x2 = cplex.prod(totalLoad[0], totalLoad[0]);
			IloNumExpr x3 = cplex.prod(LR, x2);
			IloNumExpr x4 = cplex.prod(-2*LR, cplex.prod(flexDemand0[0], totalLoad[0]));
			double x5 = LR * flexDemand0[0] * flexDemand0[0];
			IloNumExpr objective = cplex.sum(x5, cplex.sum(x1, x3, x4)) ;
	
			for(int i=1; i<24; i++){
				IloNumExpr objective1 = objective;
				x1 = cplex.prod(signal[i], totalLoad[i]);
				x2 = cplex.prod(totalLoad[i], totalLoad[i]);
				x3 = cplex.prod(LR, x2);
				x4 = cplex.prod(-2*LR, cplex.prod(flexDemand0[i], totalLoad[i]));
				x5 = LR * flexDemand0[i] * flexDemand0[i];
				objective = cplex.sum(cplex.sum(x5, cplex.sum(x1, x3, x4)), objective1) ;
			}
			
			//define objective
			cplex.addMinimize(objective);
			
			//CONSTRAINTS
			
			//initial amount of energy in the thermal and electrical stores passed from previous day
			availableEleEnergy[t1] = cplex.sum(initEleStore, netEleEnergy[t1]); //0.9*maxEleCap = initial amount of energy in a store
			availableHeatEnergy[t1] = cplex.sum(initHeatStore, netHeatEnergy[t1]); //0.9*maxHeatCap = initial amount of energy in a store
			availableEVEnergy[t1] = cplex.sum(SOC1 * EVcap, cplex.prod(chargeEV[t1], effEV));
			
			for(int j=t1+1; j<24+t1; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[k],netEleEnergy[i]);
				availableHeatEnergy[i] = cplex.sum(availableHeatEnergy[k],netHeatEnergy[i]);
			}
			
			for(int i=1; i<EVlength; i++){
				availableEVEnergy[i] = cplex.sum(availableEVEnergy[i-1],cplex.prod(chargeEV[i],effEV));
			}
			
			//C1
			cplex.addLe(dischargeEle[t1], initEleStore);
			//C2
			cplex.addEq(availableEleEnergy[t2],initEleStore);
			//C3
			cplex.addEq(availableHeatEnergy[t2],initHeatStore);
			//C4
			cplex.addLe(availableEleEnergy[t1], maxEleCap);
			//C5
			cplex.addLe(availableHeatEnergy[t1], maxHeatCap);
			//C6
			cplex.addLe(cplex.sum(chargeEle[0], dischargeEle[0]), maxElePower);
			//C7
			cplex.addEq(availableEVEnergy[EVlength-1], EVcap*SOC2);;
			
			//C7-151
			for(int j=t1+1; j<t1+24; j++){
				int i=j%24;
					cplex.addLe(dischargeEle[i], availableEleEnergy[(i+23)%24]);
					cplex.addLe(availableEleEnergy[i], maxEleCap);
					cplex.addLe(availableHeatEnergy[i], maxHeatCap);
					cplex.addLe(0, availableEleEnergy[i]);
					cplex.addLe(0, availableHeatEnergy[i]);
					cplex.addLe(cplex.sum(chargeEle[i], dischargeEle[i]), maxElePower);
				}
			//C152+
			for(int i=0; i<EVlength; i++){
				cplex.addLe(availableEVEnergy[i], EVcap);
			}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int j=t1; j<t1+24; j++){
					int i=j%24;
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					storageEleProfile[i] = chargeEleProfile[i]-dischargeEleProfile[i];
					demandProfileHP[i]=cplex.getValue(demandHP[i]);
				}
				
				for(int i=0; i<EVlength; i++){
					int j=(EVstart+i)%24;
					demandEV[j]=cplex.getValue(chargeEV[i]);
				}
				
				initEleStore=cplex.getValue(availableEleEnergy[t2]);
				initHeatStore=cplex.getValue(availableHeatEnergy[t2]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
			}
			//Releases the IloCplex object and the associated objects created by calls of the methods of the invoking object.
			cplex.end();
			cplex = null;
		}
		catch (IloException exc){
			exc.printStackTrace();
		}
	}

	public static void optimiseDemandWithThermWithEVOnPrice(
			double maxHeatCap, double maxEleCap, //energy capacity of heat and electrical storage
			double maxElePower, double maxThermPower, //electrical and thermal power constraints
			double maxHPower, //nominal capacity for the heat pump 
			double effEle, double COP, double effTherm, //battery efficiency, coefficient of performance for the heat pump, efficiency of thermal storage
			double[] signal, //optimisation signal sent to consumers 
			double[] chargeEleProfile, double[] dischargeEleProfile, double[] storageEleProfile, //charging and discharging profile for the battery
			int t1, //start time slot for EV optimisation
			double[] demandProfileHP,//charging and discharging profile for the battery
			double[] heatDemand, double[] demandEleBL, 
			double[] genProfile,
			double initEleStore, double initHeatStore,
			double SOC1, double SOC2, double[] demandEV, double EVcap, 
			double maxEVpower, int EVstart, int EVfinish, double effEV){
		try {
			IloCplex cplex = new IloCplex ();
			
			//determining the number of hours for charging
			int EVlength;
			if(EVfinish>EVstart){
				EVlength=EVfinish-EVstart;
			}else{
				EVlength=EVfinish+24-EVstart;
			}
			
			double[] chargeThermProfile = new double[24];
			double[] dischargeThermProfile = new double[24];
			
			//decision variables
			IloNumVar[] chargeEle = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeEle = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			IloNumVar[] chargeTherm = new IloNumVar[24]; //charge by an electrical store
			IloNumVar[] dischargeTherm = new IloNumVar[24]; //discharge by an electrical store to fulfill baseload
			IloNumVar[] chargeEV = new IloNumVar[EVlength]; //demand by the heat pump independently
			
			for (int i=0; i<24; i++){
				chargeEle[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeEle[i] = cplex.numVar(0, maxElePower);
				chargeTherm[i] = cplex.numVar(0, maxElePower); //charge P is bound by maxP
				dischargeTherm[i] = cplex.numVar(0, maxElePower);
			}
			
			for (int i=0; i<EVlength; i++){
				chargeEV[i] = cplex.numVar(0, maxEVpower);
			}
			
			//DEFINITIONS
			IloNumExpr[] availableEleEnergy = new IloNumExpr[24]; //refers to the energy in the battery
			IloNumExpr[] availableHeatEnergy = new IloNumExpr[24]; //refers to the energy in the thermal storage
			IloNumExpr[] netEleEnergy = new IloNumExpr[24]; //net demand for power by electrical store
			IloNumExpr[] netHeatEnergy = new IloNumExpr[24]; //net demand for power by thermal store
			IloNumExpr[] totalLoad = new IloNumExpr[24]; //total flexible electrical demand by consumer
			IloNumExpr[] demandEleHP = new IloNumExpr[24]; //thermal energy going into thermal storage
			IloNumExpr[] demandThermHP = new IloNumExpr[24]; //thermal energy going into thermal storage
			IloNumExpr[] availableEVEnergy = new IloNumExpr[EVlength]; //refers to the energy in the EV
						
			//electrical HP demand converted to heat demand using COP (either from the battery of from HP)
			for(int i=0; i<24; i++){
				//total thermal demand from HP
				demandThermHP[i]=cplex.sum(cplex.sum(heatDemand[i],chargeTherm[i]),cplex.prod(-1, dischargeTherm[i]));
				//total electricity demand from HP
				demandEleHP[i]=cplex.prod(1/COP, demandThermHP[i]); 
			}
		
			//calculate net charged energy in electrical and thermal storage
			for(int j=t1; j<24+t1; j++){
				int i=j%24;
				netEleEnergy[i]=cplex.sum(cplex.prod(effEle,chargeEle[i]), cplex.prod(-effEle,dischargeEle[i])); //net storage for battery
				netHeatEnergy[i]=cplex.sum(chargeTherm[i], -1.0 *heatDemand[i]);//net storage for thermal store
				//total load = charge from EV + demand from HP + demand from electrical storage - discharge of electrical storage
				totalLoad[i]=cplex.sum(demandEleHP[i], cplex.sum(chargeEle[i], cplex.prod(-effEle,dischargeEle[i]))); //total flexible load
			}
			
			for(int i=0; i<EVlength; i++){
				int j=(EVstart+i)%24;
				totalLoad[j]=cplex.sum(chargeEV[i], totalLoad[j]);
			}
			
			IloNumExpr x1 = cplex.prod(signal[0], totalLoad[0]);
			IloNumExpr objective = x1;
	
			for(int i=1; i<24; i++){
				IloNumExpr objective1 = objective;
				x1 = cplex.prod(signal[i], totalLoad[i]);
				objective = cplex.sum(objective1, x1);
			}
			
			//define objective
			cplex.addMinimize(objective);
			
			//CONSTRAINTS
			
			//initial amount of energy in the thermal and electrical stores passed from previous day
			availableEleEnergy[0] = cplex.sum(initEleStore, netEleEnergy[0]); //0.9*maxEleCap = initial amount of energy in a store
			availableHeatEnergy[0] = cplex.sum(initHeatStore, netHeatEnergy[0]); //0.9*maxHeatCap = initial amount of energy in a store
			availableEVEnergy[t1] = cplex.sum(SOC1 * EVcap, cplex.prod(chargeEV[t1], effEV));
			
			for(int i=1; i<24; i++){
				availableEleEnergy[i] = cplex.sum(availableEleEnergy[i-1],netEleEnergy[i]);
				availableHeatEnergy[i] = cplex.sum(availableHeatEnergy[i-1],netHeatEnergy[i]);
			}
			
			for(int j=t1+1; j<t1+EVlength; j++){
				int i=j%24;
				int k=(i+23)%24;
				availableEVEnergy[i] = cplex.sum(availableEVEnergy[k],cplex.prod(chargeEV[i],effEV));
			}
			
			//C1
			cplex.addLe(cplex.sum(chargeEle[0], dischargeEle[0]), maxElePower);
			cplex.addEq(availableEVEnergy[EVlength-1], EVcap*SOC2);;
			
			//C7-151
			for(int i=0; i<24; i++){
					cplex.addLe(demandEleHP[i],maxHPower);
					cplex.addLe(availableEleEnergy[i], maxEleCap);
					cplex.addLe(availableHeatEnergy[i], maxHeatCap);
					cplex.addLe(0, availableEleEnergy[i]);
					cplex.addLe(0, availableHeatEnergy[i]);
					cplex.addLe(cplex.sum(chargeEle[i], dischargeEle[i]), maxElePower);
					cplex.addLe(cplex.sum(chargeEle[i], dischargeEle[i]), maxElePower);
				}
			//C152+
			for(int i=0; i<EVlength; i++){
				cplex.addLe(availableEVEnergy[i], EVcap);
			}
			
			String file = new String("ModelLP.lp");
			
			cplex.exportModel(file);
			
			//solve
			if (cplex.solve()) {
				for(int i=0; i<t1+24; i++){
					chargeEleProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeEleProfile[i]=cplex.getValue(dischargeEle[i]);
					chargeThermProfile[i]=cplex.getValue(chargeEle[i]);
					dischargeThermProfile[i]=cplex.getValue(dischargeEle[i]);
					storageEleProfile[i] = chargeEleProfile[i]-dischargeEleProfile[i];
					demandProfileHP[i]=(heatDemand[i]+chargeThermProfile[i]-dischargeThermProfile[i])/COP;
					demandEV[i]=cplex.getValue(chargeEV[i]);
				}
				
				initEleStore=cplex.getValue(availableEleEnergy[23]);
				initHeatStore=cplex.getValue(availableHeatEnergy[23]);
				//System.out.println("chargeProfile = " + Arrays.toString(chargeProfile));
				//System.out.println("dischargeProfile = " + Arrays.toString(dischargeProfile));
				//System.out.println("storageProfile = " + Arrays.toString(storageProfile));
			}else{
				System.out.println("Model not solved");
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
