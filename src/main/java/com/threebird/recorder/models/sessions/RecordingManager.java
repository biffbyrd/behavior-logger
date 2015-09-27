package com.threebird.recorder.models.sessions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.util.Duration;

import com.google.common.collect.Lists;
import com.threebird.recorder.models.MappableChar;
import com.threebird.recorder.models.behaviors.Behavior;
import com.threebird.recorder.models.behaviors.ContinuousBehavior;
import com.threebird.recorder.models.behaviors.DiscreteBehavior;
import com.threebird.recorder.models.preferences.PreferencesManager;
import com.threebird.recorder.models.schemas.KeyBehaviorMapping;
import com.threebird.recorder.models.schemas.SchemasManager;
import com.threebird.recorder.persistence.Recordings;

public class RecordingManager
{
  public final Timeline timer;
  public final SimpleBooleanProperty saveSuccessfulProperty = new SimpleBooleanProperty( true );
  public final SimpleBooleanProperty playingProperty = new SimpleBooleanProperty( false );
  public final SimpleIntegerProperty counter = new SimpleIntegerProperty( 0 );
  public final SimpleStringProperty notes = new SimpleStringProperty();
  public final ObservableList< DiscreteBehavior > discrete = FXCollections.observableArrayList();
  public final ObservableList< ContinuousBehavior > continuous = FXCollections.observableArrayList();
  public final ObservableMap< MappableChar, KeyBehaviorMapping > unknowns = FXCollections.observableHashMap();

  public final ObservableMap< MappableChar, ContinuousBehavior > midContinuous =
      FXCollections.observableHashMap();
  public final ObservableMap< MappableChar, SimpleIntegerProperty > discreteCounts =
      FXCollections.observableHashMap();
  public final ObservableMap< MappableChar, ContinuousCounter > continuousCounts =
      FXCollections.observableHashMap();

  private final String uuid;

  public RecordingManager()
  {
    uuid = UUID.randomUUID().toString();

    timer = new Timeline();
    timer.setCycleCount( Animation.INDEFINITE );
    KeyFrame kf = new KeyFrame( Duration.millis( 1 ), evt -> {
      counter.set( counter.get() + 1 );
    } );
    timer.getKeyFrames().add( kf );

    discrete.addListener( (ListChangeListener< DiscreteBehavior >) c -> persist() );
    continuous.addListener( (ListChangeListener< ContinuousBehavior >) c -> persist() );
    playingProperty.addListener( ( o, oldV, playing ) -> {
      if (!playing) {
        persist();
      }
    } );

    notes.addListener( ( obs, old, newV ) -> {
      persist();
    } );
  }

  private void persist()
  {
    String fullFileName = getFullFileName();
    List< Behavior > behaviors = allBehaviors();
    String _notes = Optional.ofNullable( notes.get() ).orElse( "" );

    CompletableFuture< Long > fCsv =
        Recordings.saveJson( new File( fullFileName + ".raw" ), uuid, behaviors, count(), _notes );
    CompletableFuture< Long > fXls =
        Recordings.saveXls( new File( fullFileName + ".xls" ), uuid, behaviors, count(), _notes );

    CompletableFuture.allOf( fCsv, fXls ).handleAsync( ( v, t ) -> {
      Platform.runLater( ( ) -> saveSuccessfulProperty.set( t == null ) );
      if (t != null) {
        t.printStackTrace();
      }
      return null;
    } );
  }

  private List< Behavior > allBehaviors()
  {
    ArrayList< Behavior > behaviors = Lists.newArrayList();
    behaviors.addAll( discrete );
    behaviors.addAll( continuous );
    Collections.sort( behaviors, Behavior.comparator );
    return behaviors;
  }

  public static String getFileName()
  {
    if (SchemasManager.getSelected() == null) {
      return null;
    }

    return PreferencesManager.filenameComponents().stream()
                             .filter( comp -> comp.enabled )
                             .map( comp -> comp.getComponent() )
                             .collect( Collectors.joining( "-" ) );
  }

  public static String getFullFileName()
  {
    if (SchemasManager.getSelected() == null) {
      return null;
    }
    String directory = SchemasManager.getSelected().sessionDirectory.getPath();
    String filename = getFileName();
    return String.format( "%s%s%s", directory, File.separator, filename );
  }

  public void togglePlayingProperty()
  {
    playingProperty.set( !playingProperty.get() );
  }

  /**
   * @return the current count of the timer, in milliseconds
   */
  public int count()
  {
    return counter.get();
  }

  public void log( DiscreteBehavior db )
  {
    discrete.add( db );
  }

  public void log( ContinuousBehavior cb )
  {
    continuous.add( cb );
  }
}
