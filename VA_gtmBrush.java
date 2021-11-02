// Grount-Truth marking tool (brush)
// ImageJ-plugin by S.B
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.tool.PlugInTool;
import ij.process.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.util.Vector;

public class VA_gtmBrush extends PlugInTool implements Runnable, ImageListener {
  private static final String BRUSH_WIDTH_KEY = "roibrushimg.width";
  private static final String OVL_OPACITY_KEY = "roibrushimg.dencity";
  private static final String EDAUTO_SAVE_KEY = "roibrushimg.autosave";
  private static final String LOC_KEY = "roibrushimg.loc";
  private static boolean      SHAPE_ROI = true; // 'this-time-state choice' between ImageRoi and ShapeRoi
  private boolean        m_bAutoSave; // do auto-save by [<<] or [>>]
  private int            m_nWidth; // brush-width 1-100
  private int            m_nOpaci; // brush-opacity for ImageRoi  1-10
  private ImagePlus      m_OImp = null; // active frame (with some editable overlay)
  private GenericDialog  m_GD = null; // control dialog
  private Button         m_GDbtn1 = null, m_GDbtn2 = null, m_GDbtn3 = null; // control buttons
  private String         m_sDir = "", m_sName = "", m_sOvlPath = ""; // the files of active frame and coupled overlay
  private boolean        m_bUpdateRoi = false; // have updates for auto-save
  private boolean        m_bCurrentImage = false; // set by iCurrentImage; true if "environment_active_frame=OImp'
  
  // services
  private String iValidateAttributes() { // validate attributes of active frame
    boolean qExists = (new File(m_sOvlPath)).exists();
    if (m_GD!=null) {
      m_GDbtn3.setEnabled( qExists ); 
    };
    return m_sDir+" \n"+m_sName+" ";
  }
  private String iGetBMPName() { // name.* => name.bmp
    int qPosDot = m_sName.lastIndexOf( '.' ); if (qPosDot<0) qPosDot = m_sName.length();
//    return m_sName.substring( 0, qPosDot )+".bmp";
    return m_sName.substring( 0, qPosDot )+"_GT.bmp";
  }
  private void iSetAttributes( ImagePlus imp ) { // set attributes of active frame
    if (imp!=null) { m_sDir = IJ.getDirectory( "current" ); m_sName = imp.getTitle(); m_sOvlPath = m_sDir+"GT"+File.separator+iGetBMPName(); }
              else { m_sDir = m_sName = m_sOvlPath = ""; }
    if (m_GD!=null) { MultiLineLabel qLabel = (MultiLineLabel)m_GD.getMessage(); if (qLabel!=null) qLabel.setText( iValidateAttributes() ); };
  }
  private void iAssignActiveFrame( ImagePlus imp ) { // set attributes and overlay for active frame
    if (imp==null) { m_OImp = null; iSetAttributes( null ); }
              else { imp.setRoi( (Roi)null ); imp.setOverlay( null );
                     iSetAttributes( imp ); iSetCurrentOverlay( imp ); imp.draw(); };
  }
  private ImagePlus iCurrentImage() { // returns environment_active_frame and sets bCurrentImage
    ImagePlus qImp = WindowManager.getCurrentImage(); m_bCurrentImage = qImp!=null;
    if (m_bCurrentImage && (m_OImp!=qImp)) m_bCurrentImage = false; return qImp;
  }

  // ImageListener interface
  public void imageClosed( ImagePlus imp ) { if (m_sName==imp.getTitle() || m_OImp==imp) iAssignActiveFrame( null ); }
  public void imageOpened( ImagePlus imp ) { iAssignActiveFrame( imp ); }  // IJ.log( "imageOpened" );  
  public void imageUpdated( ImagePlus imp ) { /*if (m_OImp==imp) iAssignActiveFrame( imp ); */ } // IJ.log( "imageUpdated" );  
  
  // PluginTool services
  private ImageRoi iGetImageRoi( Overlay ovl ) { // returns ImageRoi looking like 'native one from this module'
    if (ovl==null || ovl.size()<1) return null;
    Roi qRoi = ovl.get(0); if (!(qRoi instanceof ImageRoi)) return null;
    return (ImageRoi)qRoi;
  }

  private void iSetCurrentOverlay( ImagePlus imp ) { // assign
    m_OImp = imp; m_bUpdateRoi = false;
    if (SHAPE_ROI) { // ShapeRoi
      Roi              qRoi = m_OImp.getRoi();
      if (qRoi==null || !(qRoi instanceof ShapeRoi)) {
        ImagePlus      qOImp = (new Opener()).openImage( m_sOvlPath );
        if (qOImp!=null) {
          BinaryProcessor qIP = new BinaryProcessor( (ByteProcessor)qOImp.getProcessor() );
          qIP.invert(); qOImp.setProcessor( qIP );
          IJ.run( qOImp, "Create Selection", "" ); // it might take long time
          m_OImp.setRoi( qOImp.getRoi() );
        };
      };
    } // ShapeRoi
    else { // ImageRoi
      ImageRoi          qOImageRoi = iGetImageRoi( m_OImp.getOverlay() );
      if (qOImageRoi==null) {
        ColorProcessor  qOIPc = new ColorProcessor( m_OImp.getWidth(), m_OImp.getHeight() );
        ImagePlus       qOImp = (new Opener()).openImage( m_sOvlPath );
        if (qOImp!=null) {
          ByteProcessor qIP = qOImp.getProcessor().convertToByteProcessor();
          qOIPc.setChannel( 1, qIP ); qOIPc.setChannel( 2, qIP ); // IJ.log( "- Overlay load for "+ m_sPath+m_sName );
        };
        qOImageRoi = new ImageRoi( 0, 0, qOIPc ); qOImageRoi.setOpacity( m_nOpaci/10.0 ); qOImageRoi.setZeroTransparent( true );
        m_OImp.setOverlay( new Overlay(qOImageRoi) );
      };
    }; // ImageRoi
    // m_OImageRoi.setOpacity( 1.0-m_nTrnsp/100.0 );
    // qOImageRoiN.setStrokeColor( Color.yellow );
    //m_OImageRoi.setStrokeWidth( 1 );
  }

  private void iUpdateROI( ImagePlus imp, MouseEvent e, boolean fromPressed ) {
    if (SHAPE_ROI) { // ShapeRoi
      ImageCanvas    qIC = imp.getCanvas();
      int            qx = qIC.offScreenX(e.getX()), qy = qIC.offScreenY(e.getY());
      ShapeRoi       qBrushRoi = new ShapeRoi( new OvalRoi(qx-m_nWidth/2,qy-m_nWidth/2,m_nWidth,m_nWidth) );
      Roi            qRoi = imp.getRoi();
      if (e.isAltDown()) {
        if (qRoi!=null) {
          IJ.showStatus( m_sName+" -" ); 
          if (!(qRoi instanceof ShapeRoi)) qRoi = new ShapeRoi( qRoi );
          ((ShapeRoi)qRoi).not( qBrushRoi );
        };
      } 
      else {
        IJ.showStatus( m_sName+" +" );
        if (qRoi!=null) { if (!(qRoi instanceof ShapeRoi)) qRoi = new ShapeRoi( qRoi );
                          ((ShapeRoi)qRoi).or( qBrushRoi ); }
                   else qRoi = qBrushRoi;
      };
      if (qRoi!=null) {
        // qRoi.setStrokeWidth( 1 ); qRoi.setFillColor( Color.blue );
        imp.setRoi( qRoi );
      };
    } // ShapeRoi
    else { // ImageRoi
      ImageRoi       qImageRoi = iGetImageRoi( imp.getOverlay() ) ;
      ImageProcessor qOIP = qImageRoi.getProcessor();
      if (fromPressed) {
        if (e.isAltDown()) { IJ.showStatus( m_sName+" -" ); qOIP.setColor( 0 ); }
                      else { IJ.showStatus( m_sName+" +" ); qOIP.setColor( Color.yellow ); };
      };
      ImageCanvas    qIC = imp.getCanvas();
      int            qx = qIC.offScreenX(e.getX()), qy = qIC.offScreenY(e.getY());
      qOIP.setLineWidth( m_nWidth ); if (fromPressed) qOIP.moveTo( qx, qy ); qOIP.lineTo( qx, qy );
      qImageRoi.setProcessor( qOIP ); imp.draw();
    }; // ImageRoi
  }

  // PluginTool interface
  public void run( String AArg ) {
    m_nWidth = Prefs.getInt( BRUSH_WIDTH_KEY, 10 );
    m_nOpaci = Prefs.getInt( OVL_OPACITY_KEY, 5 );
    m_bAutoSave = Prefs.getBoolean( EDAUTO_SAVE_KEY, true );
    Toolbar.addPlugInTool( this );
  }
  public void mousePressed( ImagePlus imp, MouseEvent e ) { iSetAttributes( imp ); iSetCurrentOverlay( imp ); iUpdateROI( imp, e, true ); }
  public void mouseDragged( ImagePlus imp, MouseEvent e ) { m_bUpdateRoi = true; iUpdateROI( imp, e, false ); }
  public void mouseReleased( ImagePlus imp, MouseEvent e ) { }
  public String getToolName() { return "gtmBrush"; }
  public String getToolIcon() { return "Cff0O11ffCcc1V66ff"; }
  public void showPopupMenu( java.awt.event.MouseEvent e, Toolbar tb ) { // IJ.log( "showPopupMenu "+Toolbar.getToolName() );
    ImagePlus         qImp = iCurrentImage(); iAssignActiveFrame( qImp );
    if (qImp!=null) showOptionsDialog();
    else
      if (m_GD!=null)
        IJ.showMessage( "gtmBrush", "Close Control Options' dialog to preset Opacity" );
      else {
        GenericDialog qGD = new GenericDialog( "gtmBrush preset Opacity" );
        qGD.addNumericField( "0:'ShapeRoi', 1-10:'ImageRoi'", SHAPE_ROI?0:m_nOpaci, 0 ); qGD.showDialog();
        if (!qGD.wasCanceled()) {
          int         qOpaci = (int)qGD.getNextNumber();
          if (!qGD.invalidNumber())
            if (qOpaci<1 || qOpaci>10) SHAPE_ROI = true;
                                  else { SHAPE_ROI = false; m_nOpaci = qOpaci; Prefs.set( OVL_OPACITY_KEY, m_nOpaci ); }
        };
      };
  }

  // modeless dialog
  public void run() { new Options( this ); } // called by showOptionsDialog
  public void showOptionsDialog() {
    Thread qT = new Thread( this, "gtmBrush Control Options" ); qT.setPriority( Thread.MIN_PRIORITY ); qT.start();
  }
  class Options implements DialogListener {
    private VA_gtmBrush m_Owner; // the owner
    Options( VA_gtmBrush AOwner ) { m_Owner = AOwner; if (m_GD!=null) m_GD.toFront(); else showDialog(); }
    private void iSaveCurrent( boolean AExplicit ) { // saves coupled overlay
      m_bUpdateRoi = false;
      int                qPosFSep = m_sOvlPath.lastIndexOf( File.separator );
      if (qPosFSep<0 || m_OImp==null) { if (AExplicit) IJ.error( "Nothing to save" ); }
      else { // attributes are Ok
        File             qNewDir = new File( m_sOvlPath.substring(0,qPosFSep+1) );
        if (!qNewDir.exists() && !qNewDir.mkdirs()) IJ.error( "Can't create "+qNewDir.getName() );
        else { // couple-directory is Ok
          ImageProcessor qIP;
          if (SHAPE_ROI) { // ShapeRoi
            qIP = new ByteProcessor( m_OImp.getWidth(), m_OImp.getHeight() );
            ShapeRoi     qRoi = (ShapeRoi)m_OImp.getRoi(); 
            if (qRoi!=null) { Rectangle qB = qRoi.getBounds(); qIP.copyBits( qRoi.getMask(), qB.x, qB.y, Blitter.COPY ); }
                       else IJ.log( "WRN: save empty BMP for ShapeRoi=null" );
          } // ShapeRoi
          else { // ImageRoi
            qIP = iGetImageRoi( m_OImp.getOverlay() ).getProcessor().convertToByteProcessor();
            qIP.threshold( 1 );
          }; // ImageRoi
          ImagePlus      qImp = new ImagePlus( m_Owner.iGetBMPName(), qIP );
          FileSaver      qFS = new FileSaver( qImp ); // qImp.show();
          if (!qFS.saveAsBmp( m_sOvlPath )) IJ.error( "Can't save "+m_sOvlPath ); // IJ.log( "Save "+qS ); //???
                                       else iSetAttributes( null );
        }; // couple-directory is Ok
      }; // attributes are Ok
    }

    public void showDialog() {
      m_GD = new NonBlockingGenericDialog( "gtmBrush Control Options" );
      m_GDbtn1 = new Button( "Reload" );
      m_GDbtn1.addActionListener( new ActionListener(){ public void actionPerformed( ActionEvent e ) { 
        m_OImp.setRoi( (Roi)null ); m_OImp.setOverlay( null ); iAssignActiveFrame( m_OImp );
      }; } ); // btn1.Listener
      m_GDbtn2 = new Button( " Save " );
      m_GDbtn2.addActionListener( new ActionListener(){ public void actionPerformed( ActionEvent e ) { 
        iSaveCurrent( true );
      }; } ); // btn2.Listener
      m_GDbtn3 = new Button( "Delete" );
      m_GDbtn3.addActionListener( new ActionListener(){ public void actionPerformed( ActionEvent e ) { 
        if (!(new File(m_sOvlPath)).delete()) IJ.error( "can't delete "+m_sOvlPath );
                                         else { m_OImp.setRoi( (Roi)null ); m_OImp.setOverlay( null ); iAssignActiveFrame( m_OImp ); };
      }; } ); // btn3.Listener
      Button  qBtn4 = new Button( " << " );
      qBtn4.addActionListener( new ActionListener(){ public void actionPerformed( ActionEvent e ) { 
        if (m_bAutoSave && m_bUpdateRoi) iSaveCurrent( false );
        (new NextImageOpener()).run( "backwardsc" ); iAssignActiveFrame( iCurrentImage() );
      }; } ); // btn4.Listener
      Button  qBtn5 = new Button( " >> " );
      qBtn5.addActionListener( new ActionListener(){ public void actionPerformed( ActionEvent e ) { 
        if (m_bAutoSave && m_bUpdateRoi) iSaveCurrent( false );
       (new NextImageOpener()).run( "forwardsc" ); iAssignActiveFrame( iCurrentImage() );
      }; } ); // btn5.Listener
      // non-functional layout just for for interior
      final Panel        qBtnPan = new Panel();
	  GridBagLayout      qGBL = new GridBagLayout(); qBtnPan.setLayout( qGBL );
      GridBagConstraints qGBC = new GridBagConstraints(); qGBC.anchor = GridBagConstraints.WEST;
      qGBC.insets = new Insets(0,0,0, 5); qGBL.setConstraints( m_GDbtn1, qGBC ); qBtnPan.add( m_GDbtn1 );
      qGBC.insets = new Insets(0,0,0, 5); qGBL.setConstraints( m_GDbtn2, qGBC ); qBtnPan.add( m_GDbtn2 );
      qGBC.insets = new Insets(0,0,0,15); qGBL.setConstraints( m_GDbtn3, qGBC ); qBtnPan.add( m_GDbtn3 );
      qGBC.insets = new Insets(0,0,0, 5); qGBL.setConstraints( qBtn4, qGBC ); qBtnPan.add( qBtn4 );
      qGBC.insets = new Insets(0,0,0, 5); qGBL.setConstraints( qBtn5, qGBC ); qBtnPan.add( qBtn5 );
      m_GD.addPanel( qBtnPan, GridBagConstraints.WEST, new Insets(0,0,0,0) );
      qGBL = (GridBagLayout)m_GD.getLayout();
      qGBC = qGBL.getConstraints( qBtnPan ); qGBC.gridx = 1;
      qGBL.setConstraints( qBtnPan, qGBC );
      // sliders
      m_GD.addCheckbox( "Auto-save", m_bAutoSave );
      m_GD.addSlider( "Width", 1, 100, m_nWidth );
      if (!SHAPE_ROI) m_GD.addSlider( "Opacity", 1, 10, m_nOpaci ); // for ImageRoi
      // standard buttons
      m_GD.setInsets( 10, 10, 0 ); m_GD.addMessage( iValidateAttributes(), null, Color.darkGray );
      m_GD.hideCancelButton(); m_GD.setOKLabel( "Close" );
      m_GD.addHelp(
        "<html><h2>ground-truth marking Brush</h2><font size=-1>"+
        "This brush draws (erases when key 'ALT' is pressed) an 'overlay' associated with './GT/CurrentImage.bmp'<br>"+
        "'CurrentImage.ext' remains unchanged. Do not operate with 'overlays' manually when this tool is active.<br>"+
        "<font color=blue>[Reload]</font> reloads 'overlay'<br>"+
        "<font color=blue>[Save]</font> saves 'overlay'<br>"+
        "<font color=red>[Delete]</font> deletes 'overlay'<br>"+
        "<font color=blue>[&lt&lt]</font> open Previous'<br>"+
        "<font color=blue>[&gt&gt]</font> open Next'<br>"+
        "</font>" );
      // show the dialog
      //iSetAttributes( null );
      m_GD.addDialogListener( this );
      Point              qLoc = Prefs.getLocation( LOC_KEY );
      if (qLoc!=null) { m_GD.centerDialog( false ); m_GD.setLocation( qLoc ); }
      ImagePlus.addImageListener( m_Owner );
      m_GD.showDialog();
      ImagePlus.removeImageListener( m_Owner );
      Prefs.saveLocation( LOC_KEY, m_GD.getLocation() );
      m_GDbtn1 = m_GDbtn2 = m_GDbtn3 = null; m_GD = null;
      iAssignActiveFrame( null );
    }

    public boolean dialogItemChanged( GenericDialog m_GD, AWTEvent e ) {
      m_nWidth = (int)m_GD.getNextNumber(); if (m_GD.invalidNumber() || m_nWidth<1 || m_nWidth>100) m_nWidth = 10;
      Prefs.set( BRUSH_WIDTH_KEY, m_nWidth ); 
      if (!SHAPE_ROI) { // for ImageRoi
        int qOpaci = (int)m_GD.getNextNumber(); if (m_GD.invalidNumber() || qOpaci<1 || qOpaci>10) qOpaci = 5;
        if (m_nOpaci!=qOpaci) { m_nOpaci = qOpaci;
          ImagePlus qImp = iCurrentImage();
          if (m_bCurrentImage) { iGetImageRoi(qImp.getOverlay()).setOpacity( m_nOpaci/10.0 ); qImp.draw(); };
        };
      };
      m_bAutoSave = m_GD.getNextBoolean(); Prefs.set( EDAUTO_SAVE_KEY, m_bAutoSave ); 
      return true;
    }
  } // of modeless dialog

} // end of public class VA_gtmBrush