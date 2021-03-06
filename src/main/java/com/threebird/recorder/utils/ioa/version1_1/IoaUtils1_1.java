package com.threebird.recorder.utils.ioa.version1_1;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.threebird.recorder.models.schemas.KeyBehaviorMapping;
import com.threebird.recorder.persistence.GsonUtils;
import com.threebird.recorder.persistence.WriteIoaIntervals;
import com.threebird.recorder.persistence.WriteIoaTimeWindows;
import com.threebird.recorder.persistence.recordings.RecordingRawJson1_1.ContinuousEvent;
import com.threebird.recorder.persistence.recordings.RecordingRawJson1_1.DiscreteEvent;
import com.threebird.recorder.persistence.recordings.RecordingRawJson1_1.SessionBean1_1;
import com.threebird.recorder.utils.ioa.IntervalCalculations;
import com.threebird.recorder.utils.ioa.IoaMethod;
import com.threebird.recorder.utils.ioa.KeyToInterval;
import com.threebird.recorder.utils.ioa.TimeWindowCalculations;
import com.threebird.recorder.views.ioa.IoaTimeBlockSummary;
import com.threebird.recorder.views.ioa.IoaTimeWindowSummary;

import javafx.scene.layout.VBox;

public class IoaUtils1_1
{
  public static KeyToInterval partition( HashMap< String, ArrayList< Integer > > stream,
                                  long totalTimeMilles,
                                  int size )
  {
    HashMap< String, Multiset< Integer > > idToIntervals = Maps.newHashMap();

    stream.forEach( ( buuid, ints ) -> {
      HashMultiset< Integer > times = HashMultiset.create();
      idToIntervals.put( buuid, times );
      ints.forEach( t -> {
        times.add( t / size );
      } );
    } );

    int numIntervals = (int) Math.ceil( (totalTimeMilles / 1000.0) / size );

    return new KeyToInterval( idToIntervals, numIntervals, size );
  }

  public static VBox processTimeBlock( IoaMethod method,
                                       int blockSize,
                                       boolean appendToFile,
                                       File out,
                                       SessionBean1_1 stream1,
                                       SessionBean1_1 stream2 )
      throws Exception
  {
    int size = blockSize < 1 ? 1 : blockSize;
    HashMap< String, ArrayList< Integer > > map1 = createIoaMap( stream1 );
    HashMap< String, ArrayList< Integer > > map2 = createIoaMap( stream2 );

    KeyToInterval data1 = partition( map1, stream1.duration, size );
    KeyToInterval data2 = partition( map2, stream2.duration, size );

    Map< String, IntervalCalculations > intervals =
        method == IoaMethod.Exact_Agreement
            ? IoaCalculations.exactAgreement( data1, data2 )
            : IoaCalculations.partialAgreement( data1, data2 );

    WriteIoaIntervals.write( intervals, appendToFile, out );
    return new IoaTimeBlockSummary( intervals );
  }

  public static HashMap< String, ArrayList< Integer > > createIoaMap( SessionBean1_1 bean )
  {
    HashMap< String, ArrayList< Integer > > result = Maps.newHashMap();
    populateDiscrete( bean, result );
    populateContinuous( bean, result );
    return result;
  }

  /**
   * Mutates the map
   */
  public static void populateContinuous( SessionBean1_1 stream1, HashMap< String, ArrayList< Integer > > map1 )
  {
    ImmutableMap< String, KeyBehaviorMapping > behaviors = Maps.uniqueIndex( stream1.schema.behaviors, b -> b.uuid );

    for (ContinuousEvent ce : stream1.continuousEvents) {
      String buuid = ce.behaviorUuid;
      String key = behaviors.get( buuid ).key.toString();

      if (!map1.containsKey( key )) {
        map1.put( key, Lists.newArrayList() );
      }

      int start = ce.startTime / 1000;
      int end = ce.endTime / 1000;
      for (int t = start; t <= end; t += 1) {
        map1.get( key ).add( t );
      }
    }
  }

  /**
   * Mutates the map
   */
  public static void populateDiscrete( SessionBean1_1 stream1, HashMap< String, ArrayList< Integer > > map1 )
  {
    ImmutableMap< String, KeyBehaviorMapping > behaviors = Maps.uniqueIndex( stream1.schema.behaviors, b -> b.uuid );

    for (DiscreteEvent de : stream1.discreteEvents) {
      String buuid = de.behaviorUuid;
      String key = behaviors.get( buuid ).key.toString();

      if (!map1.containsKey( key )) {
        map1.put( key, Lists.newArrayList() );
      }

      map1.get( key ).add( de.time / 1000 );
    }
  }

  public static VBox processTimeWindow( String file1,
                                        String file2,
                                        boolean appendToFile,
                                        File out,
                                        int threshold,
                                        SessionBean1_1 stream1,
                                        SessionBean1_1 stream2 )
      throws Exception
  {
    HashMap< String, ArrayList< Integer > > discretes1 = Maps.newHashMap();
    HashMap< String, ArrayList< Integer > > discretes2 = Maps.newHashMap();
    populateDiscrete( stream1, discretes1 );
    populateDiscrete( stream2, discretes2 );
    KeyToInterval discrete1 = partition( discretes1, stream1.duration, 1 );
    KeyToInterval discrete2 = partition( discretes2, stream2.duration, 1 );

    HashMap< String, ArrayList< Integer > > continuous1 = Maps.newHashMap();
    HashMap< String, ArrayList< Integer > > continuous2 = Maps.newHashMap();
    populateContinuous( stream1, continuous1 );
    populateContinuous( stream2, continuous2 );
    KeyToInterval cont1 = partition( continuous1, stream1.duration, 1 );
    KeyToInterval cont2 = partition( continuous2, stream2.duration, 1 );

    Map< String, TimeWindowCalculations > ioaDiscrete =
        IoaCalculations.windowAgreementDiscrete( discrete1, discrete2, threshold );
    Map< String, Double > ioaContinuous =
        IoaCalculations.windowAgreementContinuous( cont1, cont2 );

    WriteIoaTimeWindows.write( ioaDiscrete,
                               ioaContinuous,
                               file1,
                               file2,
                               appendToFile,
                               out );

    return new IoaTimeWindowSummary( ioaDiscrete, ioaContinuous );
  }

  /**
   * Calculates IOA and writes the output to 'out'
   * 
   * @param f1
   *          the first raw input file
   * @param f2
   *          the second raw input file
   * @param method
   *          the {@link IoaMethod} used
   * @param blockSize
   *          the blocksize of intervals used
   * @param out
   *          the output file
   * @return a JavaFX pane giving a summary of the output file
   * @throws IOException
   */
  public static VBox process( File f1,
                              File f2,
                              IoaMethod method,
                              int blockSize,
                              boolean appendToFile,
                              File out )
      throws Exception
  {
    SessionBean1_1 stream1 = GsonUtils.get( f1, new SessionBean1_1() );
    SessionBean1_1 stream2 = GsonUtils.get( f2, new SessionBean1_1() );

    if (method != IoaMethod.Time_Window) {
      return processTimeBlock( method, blockSize, appendToFile, out, stream1, stream2 );
    } else {
      return processTimeWindow( f1.getName(), f2.getName(), appendToFile, out, blockSize, stream1, stream2 );
    }
  }
}
