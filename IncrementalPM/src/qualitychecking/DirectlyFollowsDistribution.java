package qualitychecking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.processmining.framework.util.Pair;

public class DirectlyFollowsDistribution implements AbstractValueDistribution {

	Map<Pair<String, String>, Integer> directlyFollowsCountsAbsolute;
	Map<Pair<String, String>, Double> directlyFollowsCountsRelative;
	double total;
	
	public DirectlyFollowsDistribution() {
		directlyFollowsCountsAbsolute = new ConcurrentHashMap<>();
		directlyFollowsCountsRelative = new ConcurrentHashMap<>();
		total = 0.0;
	}
	
	public void addTrace(XTrace trace) {
		// update absolute counts
		for (int i = 0; i < trace.size() - 1; i++) {
			incrementCountEventClassPair(trace.get(i), trace.get(i + 1));
		}
		//update relative counts
		for (Pair<String,String> key : this.directlyFollowsCountsAbsolute.keySet()) {
			this.directlyFollowsCountsRelative.put(key, this.directlyFollowsCountsAbsolute.get(key)/total);
		}
	}
	
	public synchronized boolean addTraceAndCheckPredicate(XTrace trace, Double epsilon) {
		Map<Pair<String, String>, Double> oldFrequencies = new HashMap<>(directlyFollowsCountsRelative);
		addTrace(trace);
		Map<Pair<String, String>, Double> newFrequencies = directlyFollowsCountsRelative;
		
		double totalDiff = 0.0;
		for(Pair<String, String> pair : newFrequencies.keySet()) {
			double newValue = newFrequencies.get(pair);
			double oldValue;
			if(!oldFrequencies.containsKey(pair))
				oldValue = 0.0;
			else
				oldValue = oldFrequencies.get(pair);
			totalDiff += Math.pow(oldValue - newValue, 2);  
		}
		return Math.sqrt(totalDiff)>epsilon;
	}
	
	public double computeExternalStatistic(AbstractValueDistribution validationDistribution) {
		Set<Pair<String, String>> allKeys = new HashSet<>(this.directlyFollowsCountsAbsolute.keySet());
		DirectlyFollowsDistribution validationDistributionCast = (DirectlyFollowsDistribution) validationDistribution;
		allKeys.addAll(validationDistributionCast.directlyFollowsCountsAbsolute.keySet());

		long[] observed1 = new long[allKeys.size()];
		long[] observed2 = new long[allKeys.size()];

		int i = 0;
		for (Pair<String, String> value : allKeys) {
			if (this.directlyFollowsCountsAbsolute.containsKey(value)) {
				observed1[i] = this.directlyFollowsCountsAbsolute.get(value);  
			}

			if (validationDistributionCast.directlyFollowsCountsAbsolute.containsKey(value)) { 
				observed2[i] = validationDistributionCast.directlyFollowsCountsAbsolute.get(value);
			}
		
			i++;
		}

		ChiSquareTest cst = new ChiSquareTest(); 
		return cst.chiSquareTestDataSetsComparison(observed1, observed2);
	}
	
	public void printComparison(AbstractValueDistribution validationDistribution) {
		Set<Pair<String, String>> allKeys = new HashSet<>(this.directlyFollowsCountsAbsolute.keySet());
		DirectlyFollowsDistribution validationDistributionCast = (DirectlyFollowsDistribution) validationDistribution;
		allKeys.addAll(validationDistributionCast.directlyFollowsCountsAbsolute.keySet());

		long[] observed1 = new long[allKeys.size()];
		long[] observed2 = new long[allKeys.size()];

		int i = 0;
		for (Pair<String, String> value : allKeys) {
			if (this.directlyFollowsCountsAbsolute.containsKey(value)) {
				observed1[i] = this.directlyFollowsCountsAbsolute.get(value);  
			}

			if (validationDistributionCast.directlyFollowsCountsAbsolute.containsKey(value)) { 
				observed2[i] = validationDistributionCast.directlyFollowsCountsAbsolute.get(value);
			}
			System.out.println(observed1[i]+", "+observed2[i]);
			i++;
		}
	}
	
	public double computeTotalDistance(AbstractValueDistribution validationDistribution) {
		DirectlyFollowsDistribution validationDistributionCast = (DirectlyFollowsDistribution) validationDistribution;
		Set<Pair<String, String>> allKeys = new HashSet<>(directlyFollowsCountsRelative.keySet());
		allKeys.addAll(validationDistributionCast.directlyFollowsCountsRelative.keySet());
		double totalDiff = 0.0;
		for(Pair<String, String> key : allKeys) {
			double sampleValue = 0.0;
			double validationValue = 0.0;
			if (directlyFollowsCountsRelative.containsKey(key)) {
				sampleValue = directlyFollowsCountsRelative.get(key); 
			}
			if (validationDistributionCast.directlyFollowsCountsRelative.containsKey(key)) {
				validationValue = validationDistributionCast.directlyFollowsCountsRelative.get(key);
			}
			
			totalDiff += Math.pow(sampleValue - validationValue, 2);  
		}
		return totalDiff;
	}
	
	
	public Pair<String, String> getEventClassPair(XEvent event1, XEvent event2) {
		return new Pair<String, String>(getEventClass(event1), getEventClass(event2));
	}
	
	private void incrementCountEventClassPair(XEvent event1, XEvent event2) {
		Pair<String, String> pair = getEventClassPair(event1, event2);
		directlyFollowsCountsAbsolute.put(pair, directlyFollowsCountsAbsolute.getOrDefault(pair,0) + 1);
		total = total + 1;
	}
	
	
	private String getEventClass(XEvent event) {
		return event.getAttributes().get("concept:name").toString()+"+"+event.getAttributes().get("lifecycle:transition").toString();
	}
	
	public String toString() {
		return directlyFollowsCountsAbsolute.toString();
	}
	
	
	public DirectlyFollowsDistribution emptyCopy() {
		DirectlyFollowsDistribution newDFD = new DirectlyFollowsDistribution();
		return newDFD;
	}
	
	
	public static void main(String[] args) {
		long[] x = {48, 22, 33, 47};
		long[] y = {35, 36, 42, 27};
		double alpha = 0.05;
		int degreesOfFreedom = (x.length-1);
		
		ChiSquareTest cst = new ChiSquareTest();
		ChiSquaredDistribution x2 = new ChiSquaredDistribution(3);
		double chiSquareAlphaDoF = x2.inverseCumulativeProbability(1-0.05);
		
		System.out.println("N0: x and y conform to the same distribution");
		System.out.println("N1: x and y conform to different same distribution");
		System.out.println("alpha: "+alpha);
		System.out.println("Degrees of Freedom: "+degreesOfFreedom);
		System.out.println();
		System.out.println("X²_0: "+cst.chiSquareDataSetsComparison(x, y));
		System.out.println("X_"+alpha+"_"+degreesOfFreedom+": "+chiSquareAlphaDoF);
		System.out.println("p-Value(X²_0): "+cst.chiSquareTestDataSetsComparison(x, y));
		System.out.println();
		System.out.println("Comparing p-Values (p-Value < alpha?->reject N0):");
		System.out.println(cst.chiSquareTestDataSetsComparison(x, y)+"<"+alpha+"? ->"+(cst.chiSquareTestDataSetsComparison(x, y)<0.05?"Reject N0, Accept N1":"Do not reject N0"));
		System.out.println();
		System.out.println("Comparing X² Statistics (X² > X²_"+alpha+"_"+degreesOfFreedom+"? -> reject N0):");
		System.out.println(cst.chiSquareDataSetsComparison(x, y)+">"+chiSquareAlphaDoF+"?  -> "+(cst.chiSquareDataSetsComparison(x, y)>chiSquareAlphaDoF?"Reject N0, Accept N1":"Do not reject N0"));		
	}

}
