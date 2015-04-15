package com.threebird.recorder.controllers;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.threebird.recorder.models.preferences.FilenameComponent;
import com.threebird.recorder.models.preferences.PreferencesManager;
import com.threebird.recorder.models.schemas.Schema;
import com.threebird.recorder.models.schemas.SchemasManager;
import com.threebird.recorder.models.sessions.RecordingManager;
import com.threebird.recorder.models.sessions.SessionManager;
import com.threebird.recorder.utils.EventRecorderUtil;

/**
 * Controls the first view the user sees. All these member variables with the @FXML
 * annotation are physical objects I placed in Scene Builder and applied an
 * 'id'. The 'id' must match the variable name. Methods with an @FXML annotation
 * are triggered by events (again, specified in scene builder)
 */
public class StartMenuController
{
  @FXML private TableView< Schema > schemaTable;
  @FXML private TableColumn< Schema, String > clientCol;
  @FXML private TableColumn< Schema, String > projectCol;
  @FXML private Button createSchemaButton;
  @FXML private Button editSchemaBtn;

  @FXML private VBox rightSide;
  @FXML private VBox mappingsBox;
  @FXML private Label timeBox;

  @FXML private TextField observerField;
  @FXML private TextField therapistField;
  @FXML private TextField conditionField;
  @FXML private TextField sessionField;

  @FXML private Label filenameLbl;
  @FXML private ImageView warningImg;

  @FXML private VBox errMsgBox;

  @FXML private Button saveButton;
  @FXML private Button startButton;
  @FXML private Button helpButton;

  /**
   * load up the FXML file we generated with Scene Builder, "schemas.fxml". This
   * view is controlled by SchemasController.java
   */
  public static void toStartMenuView()
  {
    String filepath = "views/start-menu.fxml";
    StartMenuController controller = EventRecorderUtil.loadScene( filepath, "Start Menu" );
    controller.init();
  }

  private void init()
  {
    rightSide.setVisible( false );
    editSchemaBtn.setVisible( false );
    initSchemaListView();
    initSessionDetails();
  }

  /**
   * Initializes 'schemaList' and binds it to 'schemas'
   */
  private void initSchemaListView()
  {
    clientCol.setCellValueFactory( p -> new SimpleStringProperty( p.getValue().client ) );
    projectCol.setCellValueFactory( p -> new SimpleStringProperty( p.getValue().project ) );

    schemaTable.setItems( SchemasManager.schemas() );
    schemaTable.getSelectionModel()
               .selectedItemProperty()
               .addListener( this::onSchemaSelect );
    schemaTable.getSelectionModel().select( SchemasManager.getSelected() );
  }

  /**
   * Populates the 'mappingBox' with the currently selected Schema's
   * key-behavior mappings
   */
  private void populateMappingsTable( Schema schema )
  {
    mappingsBox.getChildren().clear();

    schema.mappings.values().forEach( mapping -> {
      Label contLbl = new Label();
      contLbl.setText( mapping.isContinuous ? "(cont.)" : "" );
      contLbl.setMinWidth( 45 );

      Label keyLbl = new Label( mapping.key.toString() );
      keyLbl.setMinWidth( 15 );

      Label separator = new Label( ":" );

      Label behaviorLbl = new Label( mapping.behavior );
      behaviorLbl.setWrapText( true );

      HBox hbox = new HBox( contLbl, keyLbl, separator, behaviorLbl );
      hbox.setSpacing( 5 );
      mappingsBox.getChildren().add( hbox );
    } );
  }

  private void initSessionDetails()
  {
    observerField.setText( SessionManager.getObserver() );
    therapistField.setText( SessionManager.getTherapist() );
    conditionField.setText( SessionManager.getCondition() );
    sessionField.setText( SessionManager.getSessionNumber().toString() );

    observerField.textProperty().addListener( ( o, old, newV ) -> SessionManager.setObserver( newV ) );
    therapistField.textProperty().addListener( ( o, old, newV ) -> SessionManager.setTherapist( newV ) );
    conditionField.textProperty().addListener( ( o, old, newV ) -> SessionManager.setCondition( newV ) );

    // Put in some idiot-proof logic for the session # (limit to just digits,
    // prevent exceeding max_value)
    EventHandler< ? super KeyEvent > limiter =
        EventRecorderUtil.createFieldLimiter( sessionField, "0123456789".toCharArray(), 9 );
    sessionField.setOnKeyTyped( limiter );
    sessionField.textProperty().addListener( ( o, old, newV ) -> {
      String text = sessionField.getText().trim();
      if (text.isEmpty()) {
        SessionManager.setSessionNumber( 0 );
      } else {
        SessionManager.setSessionNumber( Integer.valueOf( text.trim() ) );
      }
    } );

    SessionManager.observerProperty().addListener( ( o, old, newV ) -> updateFilenameLabel() );
    SessionManager.therapistProperty().addListener( ( o, old, newV ) -> updateFilenameLabel() );
    SessionManager.conditionProperty().addListener( ( o, old, newV ) -> updateFilenameLabel() );
    SessionManager.sessionNumberProperty().addListener( ( o, old, newV ) -> updateFilenameLabel() );
    PreferencesManager.filenameComponents().addListener( (ListChangeListener< FilenameComponent >) c -> {
      updateFilenameLabel();
    } );
  }

  private void updateFilenameLabel()
  {
    if (RecordingManager.getFileName() == null) {
      filenameLbl.setText( "" );
      return;
    }

    String text = String.format( "%s (.csv/.xls)", RecordingManager.getFileName() );
    filenameLbl.setText( text );

    boolean isConflicting = dataFilenameHasConflict();
    warningImg.setVisible( isConflicting );
    filenameLbl.setTextFill( isConflicting ? Color.ORANGE : Color.BLACK );
  }

  private boolean dataFilenameHasConflict()
  {
    String fullFileName = RecordingManager.getFullFileName();
    File fCsv = new File( fullFileName + ".csv" );
    File fXls = new File( fullFileName + ".xlsx" );

    boolean isConflicting = fCsv.exists() || fXls.exists();
    return isConflicting;
  }

  /**
   * On schema-select: if the new value is null, hide right-hand-side.
   * Otherwise, populate right-hand-side with new schema's data
   */
  private void onSchemaSelect( ObservableValue< ? extends Schema > ov,
                               Schema oldV,
                               Schema newV )
  {
    SchemasManager.setSelected( newV );
    if (newV != null) {
      rightSide.setVisible( true );
      editSchemaBtn.setVisible( true );
      populateMappingsTable( newV );
      timeBox.setText( EventRecorderUtil.secondsToTimestamp( newV.duration ) );
      updateFilenameLabel();
    } else {
      rightSide.setVisible( false );
      editSchemaBtn.setVisible( false );
    }
  }

  @FXML private void onCreateSchemaClicked( ActionEvent evt )
  {
    EditSchemaController.toEditSchemaView( null );
  }

  @FXML private void onEditSchemaClicked( ActionEvent evt )
  {
    EditSchemaController.toEditSchemaView( SchemasManager.getSelected() );
  }

  private boolean validate()
  {
    String cssRed = "-fx-background-color:#FFDDDD;-fx-border-color: #f00;";
    boolean valid = true;

    ImmutableMap< String, FilenameComponent > displayToComp =
        Maps.uniqueIndex( PreferencesManager.filenameComponents(), c -> c.name );

    Map< FilenameComponent, TextField > compToField = Maps.newHashMap();
    compToField.put( displayToComp.get( "Observer" ), observerField );
    compToField.put( displayToComp.get( "Therapist" ), therapistField );
    compToField.put( displayToComp.get( "Condition" ), conditionField );
    compToField.put( displayToComp.get( "Session Number" ), sessionField );

    for (Entry< FilenameComponent, TextField > entry : compToField.entrySet()) {
      FilenameComponent comp = entry.getKey();
      TextField field = entry.getValue();
      if (comp.enabled && field.getText().isEmpty()) {
        field.setStyle( cssRed );
        Label lbl = new Label( "- " + comp.name + " is required for your data file's name." );
        lbl.setTextFill( Color.RED );
        errMsgBox.getChildren().add( lbl );
        valid = false;
      } else {
        field.setStyle( "" );
      }
    }

    if (!valid) {
      Label lbl = new Label( "You can edit the file name in the Preferences menu." );
      lbl.setTextFill( Color.RED );
      errMsgBox.getChildren().add( lbl );
    }

    return valid;
  }

  @FXML private void onPrefsClicked( ActionEvent evt )
  {
    PreferencesController.showPreferences();
  }

  @FXML private void onStartClicked( ActionEvent evt )
  {
    if (!validate()) {
      return;
    }

    boolean isConflicting = dataFilenameHasConflict();
    if (isConflicting) {
      String msg =
          "Starting this session will overwrite an existing data file.\n"
              + "Click Cancel to stay here or Ok to proceed.";

      Alert alert = new Alert( Alert.AlertType.CONFIRMATION );
      alert.setTitle( "File already exists." );
      alert.setHeaderText( null );
      alert.setContentText( msg );
      alert.showAndWait()
           .filter( result -> result == ButtonType.OK )
           .ifPresent( r -> RecordingController.toRecordingView() );
    } else {
      RecordingController.toRecordingView();
    }
  }

  @FXML private void onIoaBtnPressed()
  {
    IoaCalculatorController.showIoaCalculator();
  }

  @FXML private void onHelpBtnPressed()
  {
    EventRecorderUtil.openManual( "start-menu" );
  }
}
