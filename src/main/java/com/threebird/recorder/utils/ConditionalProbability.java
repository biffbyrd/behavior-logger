package com.threebird.recorder.utils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.threebird.recorder.models.behaviors.BehaviorEvent;

public class ConditionalProbability {

    public static Integer RANGE_5 = 5000;
    public static Integer RANGE_10 = 10000;
    public static Integer RANGE_15 = 15000;
    public static Integer RANGE_20 = 20000;

    public static class Results {
	public double probability;
	public int sampled;
	public int total;

	public Results(double probability, int sampled, int total) {
	    this.probability = probability;
	    this.sampled = sampled;
	    this.total = total;
	}

	@Override
	public int hashCode() {
	    final int prime = 31;
	    int result = 1;
	    long temp;
	    temp = Double.doubleToLongBits(probability);
	    result = prime * result + (int) (temp ^ (temp >>> 32));
	    result = prime * result + sampled;
	    result = prime * result + total;
	    return result;
	}

	@Override
	public boolean equals(Object obj) {
	    if (this == obj)
		return true;
	    if (obj == null)
		return false;
	    if (getClass() != obj.getClass())
		return false;
	    Results other = (Results) obj;
	    if (Double.doubleToLongBits(probability) != Double.doubleToLongBits(other.probability))
		return false;
	    if (sampled != other.sampled)
		return false;
	    if (total != other.total)
		return false;
	    return true;
	}
    }

    public static class AllResults {
	public static Comparator<AllResults> compare = //
		(r1, r2) -> (int) (Math.floor(r1.avg * 1000) - Math.floor(r2.avg * 1000));

	public final Results binaryEO;
	public final Results binaryNonEO;
	public final Results proportionEO;
	public final Results proportionNonEO;
	public final Double avg;

	public AllResults(Results binaryEO, Results binaryNonEO, Results proportionEO, Results proportionNonEO) {
	    this.binaryEO = binaryEO;
	    this.binaryNonEO = binaryNonEO;
	    this.proportionEO = proportionEO;
	    this.proportionNonEO = proportionNonEO;
	    this.avg = (binaryEO.probability + binaryNonEO.probability + proportionEO.probability
		    + proportionNonEO.probability) / 4;
	}
    }

    public static AllResults all(List<BehaviorEvent> targetEvents, //
	    List<BehaviorEvent> consequentEvents, //
	    int range) {
	return new AllResults(//
		binaryEO(targetEvents, consequentEvents, range), //
		binaryNonEO(targetEvents, consequentEvents, range), //
		proportionEO(targetEvents, consequentEvents, range), //
		proportionNonEO(targetEvents, consequentEvents, range));
    }

    public static Results binaryNonEO( //
	    List<BehaviorEvent> targetEvents, //
	    List<BehaviorEvent> consequentEvents, //
	    int range) {
	int numTargets = targetEvents.size();
	double numPotentiallyReinforcedTargets = 0f;
	for (BehaviorEvent te : targetEvents) {
	    boolean hasConsequentEvents = hasConsequentEvents(consequentEvents, te, range);
	    boolean hasOverlappingEvents = hasOverlappingEvents(consequentEvents, te);
	    if (hasConsequentEvents || hasOverlappingEvents) {
		numPotentiallyReinforcedTargets++;
	    }
	}
	if (numTargets == 0) {
	    return new Results(-1.0, numTargets, numTargets);
	}
	return new Results(numPotentiallyReinforcedTargets / numTargets, //
		numTargets, numTargets);
    }

    private static boolean hasOverlappingEvents(List<BehaviorEvent> candidates, BehaviorEvent target) {
	for (BehaviorEvent ce : candidates) {
	    if (ce.startTime < target.startTime && ce.endTime() > target.startTime) {
		return true;
	    }
	}
	return false;
    }

    private static boolean hasConsequentEvents(List<BehaviorEvent> candidates, BehaviorEvent target, int range) {
	int end = target.startTime + range;
	for (BehaviorEvent ce : candidates) {
	    if (ce.startTime > target.startTime && ce.startTime < end) {
		return true;
	    }
	}
	return false;
    }

    public static Results binaryEO( //
	    List<BehaviorEvent> targetEvents, //
	    List<BehaviorEvent> consequentEvents, //
	    int range) {
	int numTargets = 0;
	double numPotentiallyReinforcedTargets = 0.0;
	for (BehaviorEvent te : targetEvents) {
	    boolean hasOverlappingEvents = hasOverlappingEvents(consequentEvents, te);
	    if (hasOverlappingEvents) {
		continue;
	    }
	    numTargets++;
	    boolean hasConsequentEvents = hasConsequentEvents(consequentEvents, te, range);
	    if (hasConsequentEvents) {
		numPotentiallyReinforcedTargets++;
	    }
	}
	if (numTargets == 0) {
	    return new Results(-1.0, numTargets, targetEvents.size());
	}
	return new Results(numPotentiallyReinforcedTargets / numTargets, numTargets, targetEvents.size());
    }

    public static Results proportionNonEO(//
	    List<BehaviorEvent> targetEvents, //
	    List<BehaviorEvent> consequentEvents, //
	    int range) {
	double totalDurationOfReinforcingConsequences = 0.0;
	for (BehaviorEvent te : targetEvents) {
	    int rangeEnd = te.startTime + range;
	    List<BehaviorEvent> matchingEvents = findConsequentEvents(consequentEvents, te, range);
	    matchingEvents.addAll(findOverlappingEvents(consequentEvents, te));
	    for (BehaviorEvent ce : matchingEvents) {
		int consequentEnd = ce.endTime();
		if (consequentEnd > rangeEnd) {
		    consequentEnd = rangeEnd;
		}
		int startTime = Math.max(ce.startTime, te.startTime);
		int duration = consequentEnd - startTime;
		totalDurationOfReinforcingConsequences += duration;
	    }
	}
	int totalMillis = range * targetEvents.size();
	if (totalMillis == 0) {
	    return new Results(-1.0, targetEvents.size(), targetEvents.size());
	}
	return new Results(totalDurationOfReinforcingConsequences / totalMillis, targetEvents.size(),
		targetEvents.size());
    }

    private static List<BehaviorEvent> findOverlappingEvents(List<BehaviorEvent> candidates, BehaviorEvent target) {
	return candidates.stream() //
		.filter((ce) -> ce.startTime < target.startTime && ce.endTime() > target.startTime) //
		.collect(Collectors.toList());
    }

    private static List<BehaviorEvent> findConsequentEvents(List<BehaviorEvent> candidates, BehaviorEvent target,
	    int range) {
	int end = target.startTime + range;
	return candidates.stream() //
		.filter((ce) -> ce.startTime > target.startTime && ce.startTime < end) //
		.collect(Collectors.toList());
    }

    public static Results proportionEO(//
	    List<BehaviorEvent> targetEvents, //
	    List<BehaviorEvent> consequentEvents, //
	    int range) {
	int numTargets = 0;
	double totalDurationOfReinforcingConsequences = 0.0;
	for (BehaviorEvent te : targetEvents) {
	    boolean hasOverlappingEvents = hasOverlappingEvents(consequentEvents, te);
	    if (hasOverlappingEvents) {
		continue;
	    }
	    numTargets++;
	    int rangeEnd = te.startTime + range;
	    List<BehaviorEvent> matchingEvents = findConsequentEvents(consequentEvents, te, range);
	    for (BehaviorEvent ce : matchingEvents) {
		int consequentEnd = ce.endTime();
		if (consequentEnd > rangeEnd) {
		    consequentEnd = rangeEnd;
		}
		int startTime = Math.max(ce.startTime, te.startTime);
		int duration = consequentEnd - startTime;
		totalDurationOfReinforcingConsequences += duration;
	    }
	}
	int totalMillis = range * numTargets;
	if (totalMillis == 0) {
	    return new Results(-1.0, numTargets, targetEvents.size());
	}
	return new Results(totalDurationOfReinforcingConsequences / totalMillis, numTargets, targetEvents.size());
    }

}