package com.behaviorlogger.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import com.behaviorlogger.models.MappableChar;
import com.behaviorlogger.models.behaviors.BehaviorEvent;
import com.behaviorlogger.models.behaviors.ContinuousBehavior;
import com.behaviorlogger.models.behaviors.DiscreteBehavior;
import com.behaviorlogger.models.schemas.KeyBehaviorMapping;
import com.behaviorlogger.persistence.recordings.RecordingRawJson1_1.ContinuousEvent;
import com.behaviorlogger.persistence.recordings.RecordingRawJson1_1.DiscreteEvent;
import com.behaviorlogger.utils.ConditionalProbability.AllResults;
import com.behaviorlogger.utils.ConditionalProbability.Results;
import com.behaviorlogger.utils.ConditionalProbability.TooManyBackgroundEventsException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ConditionalProbabilityTest {

    // Filtering Event Streams

    @Test
    public void getTargetsDiscrete() {
	List<DiscreteEvent> discreteEvents = new ArrayList<>();
	KeyBehaviorMapping target = new KeyBehaviorMapping("b1", MappableChar.B, "", false, false);
	discreteEvents.add(new DiscreteEvent("b1", 0));
	discreteEvents.add(new DiscreteEvent("b2", 0));
	discreteEvents.add(new DiscreteEvent("b1", 1));
	List<DiscreteBehavior> targetEvents = ConditionalProbability.getTargetEvents(target, discreteEvents,
		Lists.newArrayList());
	List<BehaviorEvent> expected = new ArrayList<>();
	expected.add(new DiscreteBehavior("b1", MappableChar.B, "", 0));
	expected.add(new DiscreteBehavior("b1", MappableChar.B, "", 1));
	Assert.assertTrue(targetEvents.size() == 2);
	Assert.assertTrue(targetEvents.get(0).uuid.equals("b1"));
	Assert.assertTrue(targetEvents.get(0).startTime == 0);
	Assert.assertTrue(targetEvents.get(1).uuid.equals("b1"));
	Assert.assertTrue(targetEvents.get(1).startTime == 1);
    }

    @Test
    public void getTargetsContinuous() {
	List<ContinuousEvent> continuousEvents = new ArrayList<>();
	KeyBehaviorMapping target = new KeyBehaviorMapping("c1", MappableChar.C, "", true, false);
	continuousEvents.add(new ContinuousEvent("c1", 100, 5001));
	continuousEvents.add(new ContinuousEvent("c2", 3000, 5000));
	continuousEvents.add(new ContinuousEvent("c1", 6000, 10_000));
	List<DiscreteBehavior> targetEvents = ConditionalProbability.getTargetEvents(target, Lists.newArrayList(),
		continuousEvents);
	targetEvents.sort(BehaviorEvent.comparator);
	List<BehaviorEvent> expected = new ArrayList<>();
	expected.add(new DiscreteBehavior("c1-100", MappableChar.C, "", 100));
	expected.add(new DiscreteBehavior("c1-1100", MappableChar.C, "", 1100));
	expected.add(new DiscreteBehavior("c1-2100", MappableChar.C, "", 2100));
	expected.add(new DiscreteBehavior("c1-3100", MappableChar.C, "", 3100));
	expected.add(new DiscreteBehavior("c1-4100", MappableChar.C, "", 4100));
	expected.add(new DiscreteBehavior("c1-6000", MappableChar.C, "", 6000));
	expected.add(new DiscreteBehavior("c1-7000", MappableChar.C, "", 7000));
	expected.add(new DiscreteBehavior("c1-8000", MappableChar.C, "", 8000));
	expected.add(new DiscreteBehavior("c1-9000", MappableChar.C, "", 9000));
	expected.add(new DiscreteBehavior("c1-10000", MappableChar.C, "", 10_000));
	Assert.assertTrue(targetEvents.size() == expected.size());
	List<String> targetUuids = targetEvents.stream().map(db -> db.uuid).collect(Collectors.toList());
	List<String> expectedUuids = expected.stream().map(db -> db.uuid).collect(Collectors.toList());
	Assert.assertEquals(expectedUuids, targetUuids);
    }

    @Test
    public void getConsequenceEvents() {
	List<DiscreteEvent> discreteEvents = new ArrayList<>();
	discreteEvents.add(new DiscreteEvent("b1", 0));
	discreteEvents.add(new DiscreteEvent("b2", 0));
	discreteEvents.add(new DiscreteEvent("b1", 1));
	List<ContinuousEvent> continuousEvents = new ArrayList<>();
	continuousEvents.add(new ContinuousEvent("b3", 0, 1));
	continuousEvents.add(new ContinuousEvent("b4", 0, 1));
	continuousEvents.add(new ContinuousEvent("b3", 1, 2));

	KeyBehaviorMapping consequence = new KeyBehaviorMapping("b1", MappableChar.B, "", false, false);
	List<ContinuousBehavior> targetEvents = ConditionalProbability.getConsequenceEvents(consequence, discreteEvents,
		continuousEvents);

	List<BehaviorEvent> expected = new ArrayList<>();
	expected.add(new DiscreteBehavior("b1", MappableChar.B, "", 0));
	expected.add(new DiscreteBehavior("b1", MappableChar.B, "", 1));
	Assert.assertTrue(targetEvents.size() == 2);
	Assert.assertTrue(targetEvents.get(0).uuid.equals("b1"));
	Assert.assertTrue(targetEvents.get(0).startTime == 0);
	Assert.assertTrue(targetEvents.get(1).uuid.equals("b1"));
	Assert.assertTrue(targetEvents.get(1).startTime == 1);

	consequence = new KeyBehaviorMapping("b3", MappableChar.C, "", true, false);
	targetEvents = ConditionalProbability.getConsequenceEvents(consequence, discreteEvents, continuousEvents);

	expected = new ArrayList<>();
	expected.add(new ContinuousBehavior("b3", MappableChar.C, "", 0, 1));
	expected.add(new ContinuousBehavior("b3", MappableChar.C, "", 1, 1));
	Assert.assertTrue(targetEvents.size() == 2);
	Assert.assertTrue(targetEvents.get(0).uuid.equals("b3"));
	Assert.assertTrue(targetEvents.get(0).startTime == 0);
	Assert.assertTrue(targetEvents.get(1).uuid.equals("b3"));
	Assert.assertTrue(targetEvents.get(1).startTime == 1);
    }

    // Generating Background Events

    @Test
    public void createNoneEOBackgroundEventsWithIter() {
	KeyBehaviorMapping target = new KeyBehaviorMapping("b1", MappableChar.B, "", false, false);
	int numEvents = 3;
	Iterator<Long> iter = Lists.newArrayList(0L, 0L, 1L, 1L, 2L, 2L, 3L, 3L).iterator();
	List<ContinuousBehavior> consequenceEvents = Lists.newArrayList();
	consequenceEvents.add(new ContinuousBehavior("c1", MappableChar.C, "", 0, 0));
	List<DiscreteBehavior> events = ConditionalProbability.randomBackgroundEventsWithIter(iter, target,
		consequenceEvents, numEvents);
	List<Long> expectedStartTimes = Lists.newArrayList(1L, 2L, 3L);
	List<Long> actualStartTimes = Lists.transform(events, (e) -> e.startTime);
	Assert.assertEquals(expectedStartTimes, actualStartTimes);
	Assert.assertTrue(events.stream().allMatch((e) -> e.uuid.equals(target.uuid)));
    }

    @Test
    public void createNonEOBackgroundEventsTrueRandom() throws TooManyBackgroundEventsException {
	KeyBehaviorMapping target = new KeyBehaviorMapping("b1", MappableChar.B, "", false, false);
	int numEvents = 9;
	int duration = 9;
	List<ContinuousBehavior> consequenceEvents = Lists.newArrayList();
	consequenceEvents.add(new ContinuousBehavior("c1", MappableChar.C, "", 0, 0));
	List<DiscreteBehavior> events = ConditionalProbability.randomBackgroundEvents(target, consequenceEvents,
		duration, numEvents);
	Assert.assertEquals(9, events.size());
	Assert.assertEquals(9, Sets.newHashSet(events).size());
	Assert.assertTrue(events.stream().noneMatch((e) -> e.startTime == 0));
    }

    @Test
    public void createNonEOBackgroundEventsRejectsImpossibleState() throws TooManyBackgroundEventsException {
	KeyBehaviorMapping target = new KeyBehaviorMapping("b1", MappableChar.B, "", false, false);
	int numEvents = 3;
	int duration = 3;
	List<ContinuousBehavior> consequenceEvents = Lists.newArrayList();
	consequenceEvents.add(new ContinuousBehavior("c1", MappableChar.C, "", 0, 1));

	ThrowingRunnable r = () -> ConditionalProbability.randomBackgroundEvents(target, consequenceEvents, duration,
		numEvents);
	Assert.assertThrows(ConditionalProbability.TooManyBackgroundEventsException.class, r);

	int numEvents2 = 5;
	int duration2 = 3;
	List<ContinuousBehavior> consequenceEvents2 = Lists.newArrayList();
	r = () -> ConditionalProbability.randomBackgroundEvents(target, consequenceEvents2, duration2, numEvents2);
	Assert.assertThrows(ConditionalProbability.TooManyBackgroundEventsException.class, r);
    }

    @Test
    public void createCompleteBackgroundEvents() {
	KeyBehaviorMapping target = new KeyBehaviorMapping("b1", MappableChar.B, "", false, false);
	int duration = 3;
	List<ContinuousBehavior> consequenceEvents = Lists.newArrayList();
	consequenceEvents.add(new ContinuousBehavior("c1", MappableChar.C, "", 0, 1));
	List<DiscreteBehavior> events = ConditionalProbability.completeBackgroundEvents(target, consequenceEvents,
		duration);
	Assert.assertEquals(2, Sets.newHashSet(events).size());
	List<Long> startTimes = Lists.transform(events, (e) -> e.startTime);
	Assert.assertEquals(Lists.newArrayList(2L, 3L), startTimes);
    }

    // Convert to Discrete Events

    @Test
    public void convertContinuousToDiscrete() {
	List<BehaviorEvent> continuousEvents = Lists.newArrayList();
	continuousEvents.add(new ContinuousBehavior("", MappableChar.C, "", 1000, 2000));
	List<DiscreteBehavior> events = ConditionalProbability.convertToDiscrete(continuousEvents);
	Assert.assertEquals(3, events.size());
	int t = 1000;
	for (DiscreteBehavior behaviorEvent : events) {
	    Assert.assertEquals(t, behaviorEvent.startTime);
	    t += 1000;
	}
    }

    @Test
    public void convertDiscreteToDiscrete() {
	List<BehaviorEvent> discreteEvents = Lists.newArrayList();
	discreteEvents.add(new DiscreteBehavior("", MappableChar.C, "", 1000));
	discreteEvents.add(new DiscreteBehavior("", MappableChar.C, "", 2000));
	List<DiscreteBehavior> events = ConditionalProbability.convertToDiscrete(discreteEvents);
	Assert.assertEquals(2, events.size());
	Assert.assertEquals(discreteEvents, events);
    }

    // Calculating Conditional Probabilities

    @Test
    public void binaryNonEO() {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 4000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 10000));

	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 3000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 17000, 1000));

	Results result = ConditionalProbability.binaryNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	Results expected = new Results(1.0 / 2, 2, 2);
	Assert.assertEquals(expected, result);

	expected = new Results(1.0, 2, 2);
	result = ConditionalProbability.binaryNonEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_10);
	Assert.assertEquals(expected, result);
    }

    @Test
    public void binaryEO() throws Exception {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 4000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 10000));
	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 3000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 17000, 1000));

	Results result = ConditionalProbability.binaryEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	Results expected = new Results(0.0 / 2, 1, 2);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.binaryEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_10);
	expected = new Results(1.0 / 1, 1, 2);
	Assert.assertEquals(expected, result);
    }

    @Test
    public void proportionNonEO() throws Exception {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 4000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 10000));

	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 3000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 17000, 1000));

	Results result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	Results expected = new Results(1.0 / 10, 2, 2);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_10);
	expected = new Results(2.0 / 20, 2, 2);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_20);
	expected = new Results(3.0 / 40, 2, 2);
	Assert.assertEquals(expected, result);
    }

    @Test
    public void proportionEO() throws Exception {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 4000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 10000));

	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 3000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 17000, 1000));

	Results result = ConditionalProbability.proportionEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	Results expected = new Results(0.0 / 5, 1, 2);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.proportionEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_10);
	expected = new Results(1.0 / 10, 1, 2);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.proportionEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_20);
	expected = new Results(1.0 / 20, 1, 2);
	Assert.assertEquals(expected, result);
    }

    @Test
    public void bug_negativeResults() throws Exception {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	for (int i = 0; i < 300000; i++) {
	    targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", i));
	}
	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 8000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 17000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 80000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 90000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 100000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 120000, 2000));
	Results result = ConditionalProbability.proportionEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_10);
	Assert.assertTrue(result.probability > 0);

	result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_10);
	Assert.assertTrue(result.probability > 0);
    }

    @Test
    public void stPeterExample_Attention() {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 10000)); // 1000 - 1000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 24000)); // 3000 - 4000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 25000)); // 3000 - 7000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 29000)); // 1000 - 8000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 30000)); // 2000 - 10000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 53000)); // 2000 - 12000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 66000)); // 2000 - 14000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 68000)); // 2000 - 16000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 71000)); // 1000 - 17000
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 81000)); // 2000 - 19000
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 11000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 17000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 21000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 25001, 3000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 32000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 34000, 5000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 40000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 42000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 45000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 50000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 54000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 57000, 3000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 64000, 3000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 69000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 72000, 1000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 81001, 2000));

	// non-EO
	Results result = ConditionalProbability.binaryNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	Results expected = new Results(10.0 / 10, 10, 10);
	Assert.assertEquals(expected, result);
	result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	expected = new Results(19.0 / 50, 10, 10);
	Assert.assertEquals(expected, result);

	// EO
	result = ConditionalProbability.binaryEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_5);
	expected = new Results(9.0 / 9, 9, 10);
	Assert.assertEquals(expected, result);
	result = ConditionalProbability.proportionEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_5);
	expected = new Results(17.0 / 45, 9, 10);
	Assert.assertEquals(expected, result);
    }

    @Test
    public void stPeterExample_Tangible() {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 10000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 24000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 25000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 29000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 30000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 53000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 66000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 71000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 68000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 81000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 7000, 7000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 89000, 7000));

	// non-EO
	Results result = ConditionalProbability.binaryNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	Results expected = new Results(1.0 / 10, 10, 10);
	Assert.assertEquals(expected, result);
	result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	expected = new Results(4.0 / 50, 10, 10);
	Assert.assertEquals(expected, result);
	result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_10);
	expected = new Results(6.0 / 100, 10, 10);
	Assert.assertEquals(expected, result);

	// EO
	result = ConditionalProbability.binaryEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_5);
	expected = new Results(0.0 / 9, 9, 10);
	Assert.assertEquals(expected, result);
	result = ConditionalProbability.proportionEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_5);
	expected = new Results(0.0 / 45, 9, 10);
	Assert.assertEquals(expected, result);
	result = ConditionalProbability.proportionEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_10);
	expected = new Results(2.0 / 90, 9, 10);
	Assert.assertEquals(expected, result);
    }

    @Test
    public void emptyTargets() {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 3000, 2000));
	consequentEvents.add(new ContinuousBehavior("consequence", MappableChar.C, "consequence", 17000, 1000));

	Results result = ConditionalProbability.binaryNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	Results expected = new Results(-1, 0, 0);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.binaryEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_5);
	expected = new Results(-1, 0, 0);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	expected = new Results(-1, 0, 0);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.proportionEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_5);
	expected = new Results(-1, 0, 0);
	Assert.assertEquals(expected, result);
    }

    @Test
    public void emptyConsequences() {
	List<DiscreteBehavior> targetEvents = Lists.newArrayList();
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 4000));
	targetEvents.add(new DiscreteBehavior("target", MappableChar.T, "target", 10000));

	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();

	Results result = ConditionalProbability.binaryNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	Results expected = new Results(0, 2, 2);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.binaryEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_5);
	expected = new Results(0, 2, 2);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.proportionNonEO(targetEvents, consequentEvents,
		ConditionalProbability.WINDOW_5);
	expected = new Results(0, 2, 2);
	Assert.assertEquals(expected, result);

	result = ConditionalProbability.proportionEO(targetEvents, consequentEvents, ConditionalProbability.WINDOW_5);
	expected = new Results(0, 2, 2);
	Assert.assertEquals(expected, result);
    }

    // -- Tests from the wild --

    @Test
    public void testStPeterTantrumExample() {
	ContinuousBehavior continuousTargetEvent = new ContinuousBehavior("tantrum", MappableChar.T, "tantrum",
		(35 * 60000) + 2800, 5353);
//	ContinuousBehavior continuousTargetEvent = new ContinuousBehavior("tantrum", MappableChar.T, "tantrum", (35 * 60000) + 2800, 6353);
	List<DiscreteBehavior> targetEvents = ConditionalProbability
		.convertToDiscrete(Lists.newArrayList(continuousTargetEvent));
	// T 35:02.800 (reinforced)
	// T 35:03.800 (reinforced)
	// P 35:04.000 <--- praise
	// T 35:04.800 (not sampled in EO; .2 reinforced in NonEO)
	// T 35:05.800
	// T 35:06.800
	// T 35:07.800
	// T -35:08.354- (convertToDiscrete dropped this part)

	List<ContinuousBehavior> consequentEvents = Lists.newArrayList();
	consequentEvents.add(new ContinuousBehavior("praise", MappableChar.P, "praise", (34 * 60000) + 50000, 1000));
	consequentEvents.add(new ContinuousBehavior("praise", MappableChar.P, "praise", (35 * 60000) + 4000, 1000));
	consequentEvents.add(new ContinuousBehavior("praise", MappableChar.P, "praise", (35 * 60000) + 29000, 1000));
	consequentEvents.add(new ContinuousBehavior("praise", MappableChar.P, "praise", (35 * 60000) + 30000, 1000));

	AllResults results = ConditionalProbability.all(targetEvents, consequentEvents, 10000);

	Assert.assertEquals(new Results(2 / 5.0, 5, 6), results.binaryEO);
	Assert.assertEquals(new Results(3 / 6.0, 6, 6), results.binaryNonEO);
	Assert.assertEquals(new Results(2.0 / 50.0, 5, 6), results.proportionEO);
	Assert.assertEquals(new Results(2.2 / 60.0, 6, 6), results.proportionNonEO);

	System.out.println(results.proportionEO);
    }
}
