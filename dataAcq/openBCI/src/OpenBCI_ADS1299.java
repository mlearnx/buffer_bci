
///////////////////////////////////////////////////////////////////////////////
//
// This class configures and manages the connection to the OpenBCI shield for
// the Arduino.  The connection is implemented via a Serial connection.
// The OpenBCI is configured using single letter text commands sent from the
// PC to the Arduino.  The EEG data streams back from the Arduino to the PC
// continuously (once started).  This class defaults to using binary transfer
// for normal operation.
//
// Created: Chip Audette, Oct 2013
// Modified: through April 2014
//
// Note: this class does not care whether you are using V1 or V2 of the OpenBCI
// board because the Arduino itself handles the differences between the two.  The
// command format to the Arduino and the data format from the Arduino are the same.
//
/////////////////////////////////////////////////////////////////////////////

// Modifed : Jason Farquhar (2014)
// Moved to use the JSSC interface to make not depend on processing...

// Java Simple Serial Control (JSSC) serial port interface 
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortList;
import jssc.SerialPortException;

import java.io.OutputStream; //for logging raw bytes to an output file
import java.io.IOException; //for logging raw bytes to an output file
 
class OpenBCI_ADS1299 {

	 public final static int DEBUG=0;
 
	 public final static String command_stop = "s";
	 public final static String command_start_channel_settings = "x";
	 public final static String command_startBinary = "b";
	 public final static String command_softreset = "v";
	 public final static String command_default_settings = "d";
	 public final static String[] command_deactivate_channel = {"1", "2", "3", "4", "5", "6", "7", "8"};
	 public final static String[] command_activate_channel = {"!", "@", "#", "$", "%", "^", "&", "*"};  //shift + 1-8

	 //final static int DATAMODE_TXT = 0;
	 public final static int DATAMODE_BIN = 1;
	 public final static int DATAMODE_BIN_WAUX = 2;
	 //final static int DATAMODE_BIN_4CHAN = 4;
  
	 final static int STATE_NOCOM = 0;
	 final static int STATE_COMINIT = 1;
	 final static int STATE_NORMAL = 2;
	 final static int COM_INIT_MSEC = 4000; //you may need to vary this for your computer or your Arduino
  
	 int[] measured_packet_length = {0,0,0,0,0};
	 int measured_packet_length_ind = 0;
	 int known_packet_length_bytes = 0;
  
	 final static byte BYTE_START = (byte)0xA0;
	 final static byte BYTE_END = (byte)0xC0;
  
	 private int dataBits = SerialPort.DATABITS_8;
	 private int stopBits = SerialPort.STOPBITS_1;
	 private int parity = SerialPort.PARITY_NONE;

	 int prefered_datamode = DATAMODE_BIN;

	 String openBCIPort=null;
	 public SerialPort serial_openBCI = null;
	 int openBCIBaud=-1;
	 int state = STATE_NOCOM;
	 int dataMode = -1;
	 long prevState_millis = 0;
	 //byte[] serialBuff;
	 //int curBuffIndex = 0;
	 DataPacket_ADS1299 dataPacket;
	 boolean isNewDataPacketAvailable = false;
	 OutputStream output; //for debugging  WEA 2014-01-26
	 int prevSampleIndex = 0;
	 int serialErrorCounter = 0;
  
	 //constructor
	 OpenBCI_ADS1299(String comPort, int baud, int nValuesPerPacket) throws SerialPortException {
    
		  //choose data mode
		  System.out.println("OpenBCI_ADS1299: prefered_datamode = " + prefered_datamode + ", nValuesPerPacket = " + nValuesPerPacket);
		  if (prefered_datamode == DATAMODE_BIN) {
				if ((nValuesPerPacket % 8) != 0) {
					 //must be requesting the aux data, so change the referred data mode
					 prefered_datamode = DATAMODE_BIN_WAUX;
					 System.out.println("OpenBCI_ADS1299: nValuesPerPacket = " + nValuesPerPacket + " so setting prefered_datamode to " + prefered_datamode);
				}
		  }
		  dataMode = prefered_datamode;
		  openBCIPort=comPort;

		  //allocate space for data packet
		  dataPacket = new DataPacket_ADS1299(nValuesPerPacket);

		  //prepare the serial port
		  openSerialPort(comPort, baud);
    
		  //open file for raw bytes
		  //output = createOutput("rawByteDumpFromProcessing.bin");  //for debugging  WEA 2014-01-26
	 }
  
	 //manage the serial port  
	 int openSerialPort() throws SerialPortException {
		  return openSerialPort(openBCIPort,openBCIBaud);
	 }
	 int openSerialPort(String comPort, int baud) throws SerialPortException {
		  if (serial_openBCI != null) closeSerialPort();		  
		  serial_openBCI = new SerialPort(comPort); //open the com port
		  // Need to set port parameters....
		  try { 
				serial_openBCI.openPort();
				serial_openBCI.setParams(baud, dataBits, stopBits, parity);
				serial_openBCI.purgePort(0); // clear anything in the com port's buffer    
				openBCIPort=comPort;
				openBCIBaud=baud;
		  } catch (SerialPortException e){
				System.err.println("OpenBCI_ADS1299::openSerialPort: Serial exception setting parameters:!");
				System.err.println(e);
				throw e;
		  }
		  changeState(STATE_COMINIT);
		  return 0;
	 }

	 int changeState(int newState) {
		  state = newState;
		  prevState_millis = System.currentTimeMillis();
		  return 0;
	 }

	 int updateState() {
		  if (state == STATE_COMINIT) {
				if ((System.currentTimeMillis() - prevState_millis) > COM_INIT_MSEC) {
					 changeState(STATE_NORMAL);
					 // //serial_openBCI.writeBytes(command_activates); 
					 // try{

					 // 	  //startDataTransfer(prefered_datamode);
					 // } catch (SerialPortException e){
					 // 	  changeState(STATE_COMINIT);
					 // 	  System.err.println("OpenBCI_ADS1299::updateState Serial exception caught!");
					 // 	  return -1;
					 // }		  
					 return 1; // successfull init
				}
		  }
		  return 0; // default to unsuccessfull init
	 }    

	 int closeSerialPort() {
		  if (serial_openBCI != null) {
				try {
					 serial_openBCI.closePort();
				} catch (SerialPortException e){
					 System.err.println("OpenBCI_ADS1299::closeSerialPort Serial exception caught!");
				}
				serial_openBCI = null;
				state = STATE_NOCOM;
		  }
		  return 0;
	 }
  
	 //start the data transfer using the current mode
	 int startDataTransfer() throws SerialPortException {
		  System.out.println("OpenBCI_ADS1299: startDataTransfer: using current dataMode...");
		  return startDataTransfer(dataMode);
	 }
  
	 //start data trasnfer using the given mode
	 int startDataTransfer(int mode) throws SerialPortException {
		  if (state == STATE_COMINIT) {
				System.out.println("OpenBCI_ADS1299: startDataTransfer: cannot start transfer...waiting for comms...");
				return -1;
		  }
		  System.out.println("OpenBCI_ADS1299: startDataTransfer: received command for mode = " + mode);
		  switch (mode) {
		  case DATAMODE_BIN:
				serial_openBCI.writeString(command_startBinary);
				System.out.println("OpenBCI_ADS1299: startDataTransfer: starting binary transfer");
				break;
		  }
		  dataMode=mode;
		  return 0;
	 }
  
	 void stopDataTransfer() {
		  if (serial_openBCI != null) {
				try {
					 serial_openBCI.writeString(command_stop);
					 //serial_openBCI.purgePort(0); // clear anything in the com port's buffer
				} catch (SerialPortException e){
					 System.err.println("OpenBCI_ADS1299::stopDataTransfer Serial exception caught!");
				}

		  }
	 }

	 // query state
	 public void queryState() throws SerialPortException {
		  if (serial_openBCI != null) {
				serial_openBCI.writeString("?");
		  }
	 }
  
	 //read from the serial port
	 int read() throws SerialPortException {  return read(false); }
	 int read(boolean echoChar) throws SerialPortException {
		  //get the byte
		  byte[] inByteA = serial_openBCI.readBytes();
		  if ( inByteA == null ) return -1;
		  if (echoChar) {
				System.out.println("Got " + inByteA.length + " bytes: ");
				for ( int i=0; i<inByteA.length; i++){
					 System.out.print((char)(inByteA[i]));
				}
				System.out.println();
		  }
    
		  //write raw unprocessed bytes to a binary data dump file
		  if (output != null) {
				try {
					 output.write(inByteA);   //for debugging  WEA 2014-01-26
				} catch (IOException e) {
					 System.err.println("OpenBCI_ADS1299: Caught IOException: " + e.getMessage());
					 //do nothing
				}
		  }
    
		  for ( int i=0; i<inByteA.length; i++) interpretBinaryStream(inByteA[i]);  //new 2014-02-02 WEA
		  return inByteA.length;
	 }

	 /* **** Borrowed from Chris Viegl from his OpenBCI parser for BrainBay
		 Packet Parser for OpenBCI (1-N channel binary format):

		 4-byte (long) integers are stored in 'little endian' formant in AVRs
		 so this protocol parser expects the lower bytes first.

		 Start Indicator: 0xA0
		 Packet_length  : 1 byte  (length = 4 bytes framenumber + 4 bytes per active channel + (optional) 4 bytes for 1 Aux value)
		 Framenumber    : 4 bytes (Sequential counter pf packets)
		 Channel 1 data  : 4 bytes 
		 ...
		 Channel N data  : 4 bytes
		 [Optional] Aux Value : 4 bytes
		 End Indcator:    0xC0
		 ********************************************************************* */

	 /***************************************************************************

    Byte 1: 0xA0
    Byte 2: Sample Number

EEG Data
Note: values are 24-bit signed, MSB first

    Bytes 3-5: Data value for EEG channel 1
    Bytes 6-8: Data value for EEG channel 2
    Bytes 9-11: Data value for EEG channel 3
    Bytes 12-14: Data value for EEG channel 4
    Bytes 15-17: Data value for EEG channel 5
    Bytes 18-20: Data value for EEG channel 6
    Bytes 21-23: Data value for EEG channel 6
    Bytes 24-26: Data value for EEG channel 8

Accelerometer Data
Note: values are 16-bit signed, MSB first

    Bytes 27-28: Data value for accelerometer channel X
    Bytes 29-30: Data value for accelerometer channel Y
    Bytes 31-32: Data value for accelerometer channel Z

Footer

    Byte 33: 0xC0
	 ***********************************************************************/


	 int nDataValuesInPacket = 0;
	 int localByteCounter=0;
	 int localChannelCounter=0;
	 int PACKET_readstate = 0;
	 byte[] localByteBuffer = {0,0,0,0};
	 void interpretBinaryStream(byte actbyte)
	 { 
		  //System.out.println("OpenBCI_ADS1299: PACKET_readstate " + PACKET_readstate);
		  switch (PACKET_readstate) {
		  case 0:  
				if (actbyte == (byte)(0xA0)) {          // look for start indicator
					 if( DEBUG>0 ) System.out.println("OpenBCI_ADS1299: interpretBinaryStream: found 0xA0");
					 PACKET_readstate++;
					 PACKET_readstate++; // BODGE: Seems that 2nd byte is now just the sample number
				} 
				break;
		  case 1:  
				nDataValuesInPacket = ((int)actbyte) / 4 - 1;   // get number of channels
				if( DEBUG>0 ) System.out.println("OpenBCI_ADS1299: interpretBinaryStream: nDataValuesInPacket = " + nDataValuesInPacket);
				//if (nDataValuesInPacket != num_channels) { //old check, too restrictive
				if ((nDataValuesInPacket < 0) || (nDataValuesInPacket > dataPacket.values.length)) {
					 serialErrorCounter++;
					 if( DEBUG>0 ) System.out.println("OpenBCI_ADS1299: interpretBinaryStream: given number of data values (" + nDataValuesInPacket + ") is not acceptable.  Ignoring packet. (" + serialErrorCounter + ")");
					 PACKET_readstate=0;
				} else { 
					 localByteCounter=0; //prepare for next usage of localByteCounter
					 PACKET_readstate++;
				}
				break;
		  case 2: 
				//check the packet counter
				localByteBuffer[localByteCounter] = actbyte;
				localByteCounter++;
				if (localByteCounter==1) {
					 dataPacket.sampleIndex = (int)actbyte; //interpretAsInt32(localByteBuffer); //added WEA
					 if ((dataPacket.sampleIndex-prevSampleIndex) != 1) {
						  serialErrorCounter++;
						  if( DEBUG>0 ) System.out.println("OpenBCI_ADS1299: interpretBinaryStream: apparent sampleIndex jump from Serial data: " + prevSampleIndex + " to  " + dataPacket.sampleIndex + ".  Keeping packet. (" + serialErrorCounter + ")");
					 }
					 prevSampleIndex = dataPacket.sampleIndex;
					 localByteCounter=0;//prepare for next usage of localByteCounter
					 localChannelCounter=0; //prepare for next usage of localChannelCounter
					 PACKET_readstate++;
				} 
				break;
		  case 3: // get channel values, these are 24bit = 3 byte
				localByteBuffer[localByteCounter] = actbyte;
				localByteCounter++;
				if (localByteCounter==3) {
					 dataPacket.values[localChannelCounter] = interpret24BitMSBAsInt32(localByteBuffer);
					 localChannelCounter++;
					 if( DEBUG>0 ) System.out.println("OpenBCI_ADS1299: interpretBinaryStream: localChannelCounter = " + localChannelCounter);
					 if (localChannelCounter==dataPacket.values.length) {  // all EEG channels arrived !
						  PACKET_readstate++;
					 }
					 //prepare for next data channel
					 localByteCounter=0; //prepare for next usage of localByteCounter
				}
				break;
		  case 4: // get aux values, these are 16bit = 2byte
				localByteBuffer[localByteCounter] = actbyte;
				localByteCounter++;
				if (localByteCounter==2) {
					 localChannelCounter++;
					 if ( dataPacket.values.length > localChannelCounter ) // add to packet if wanted
						  dataPacket.values[localChannelCounter] = interpret16bitMSBAsInt32(localByteBuffer);
					 if( DEBUG>0 ) System.out.println("OpenBCI_ADS1299: interpretBinaryStream: localChannelCounter = " + localChannelCounter);
					 if (localChannelCounter==dataPacket.values.length+3) {  // all AUX channels arrived !
						  PACKET_readstate++;  // Move to next packet phase
					 }
					 //prepare for next data channel
					 localByteCounter=0; //prepare for next usage of localByteCounter
				}
				break;
		  case 5:
				if (actbyte == (byte)(0xC0)) {    // if correct end delimiter found:
					 isNewDataPacketAvailable = true; //original place for this.  but why not put it in the previous case block
					 PACKET_readstate=0;  // either way, look for next packet
				} else {
					 serialErrorCounter++;
					 System.out.println("OpenBCI_ADS1299: interpretBinaryStream: expecteding end-of-packet byte is missing.  Discarding packet. (" + serialErrorCounter + ")");
					 //PACKET_readstate=0;  // either way, look for next packet
				}
				break;
		  default: 
				System.out.println("OpenBCI_ADS1299: Unknown byte: " + actbyte + " .  Continuing...");
				//System.out.println("OpenBCI_ADS1299: Unknown byte.  Continuing...");
				PACKET_readstate=0;  // look for next packet
		  }
	 } // end of interpretBinaryStream


	 //activate or deactivate an EEG channel...channel counting is zero through nchan-1
	 public void changeChannelState(int Ichan,boolean activate) throws SerialPortException {
		  if (serial_openBCI != null) {
				if ((Ichan >= 0) && (Ichan < command_activate_channel.length)) {
					 if (activate) {
						  serial_openBCI.writeString(command_activate_channel[Ichan]);
					 } else {
						  serial_openBCI.writeString(command_deactivate_channel[Ichan]);
					 }
				}
		  }
	 }
  
	 //activate an EEG channel...channel counting is zero through nchan-1
	 public void activateChannel(int Ichan) {
		  if (serial_openBCI != null) {
				if ((Ichan >= 0) && (Ichan < command_activate_channel.length)) {
					 try {
						  serial_openBCI.writeString(command_activate_channel[Ichan]);
					 } catch (SerialPortException e){
						  System.err.println("OpenBCI_ADS1299::deactivateChannel Serial exception caught!");
					 }
				}
		  }
	 }

	 //deactivate an EEG channel...channel counting is zero through nchan-1
	 public void deactivateChannel(int Ichan) {
		  if (serial_openBCI != null) {
				if ((Ichan >= 0) && (Ichan < command_deactivate_channel.length)) {
					 try {
						  serial_openBCI.writeString(command_deactivate_channel[Ichan]);
					 } catch (SerialPortException e){
						  System.err.println("OpenBCI_ADS1299::deactivateChannel Serial exception caught!");
					 }
				}
		  }
	 }

	 //return the state
	 public boolean isStateNormal() { 
		  if (state == STATE_NORMAL) { 
				return true;
		  } else {
				return false;
		  }
	 }
    
	 int interpret24BitMSBAsInt32(byte[] byteArray) {     
		  int newInt = (  
							 ((0xFF & byteArray[0]) << 16) |  
							 ((0xFF & byteArray[1]) << 8) |   
							 (0xFF & byteArray[2])  
								);  
		  if ((newInt & 0x00800000) > 0) {  
				newInt |= 0xFF000000;  
		  } else {  
				newInt &= 0x00FFFFFF;  
		  }  
		  return newInt;  
	 }  
	 
	 int interpret32bitLSBAsInt32(byte[] byteArray) {     
		  //little endian
		  int i=0;
		  i=((int)(0xFF & byteArray[3]) << 24) | 
				((int)(0xFF & byteArray[2]) << 16) |
				((int)(0xFF & byteArray[1]) << 8) | 
				(int)(0xFF & byteArray[0]);
		  return i;
	 }
  
	 int interpret16bitMSBAsInt32(byte[] byteArray) {
		  int newInt = (
							 ((0xFF & byteArray[0]) << 8) |
							 (0xFF & byteArray[1])
							 );
		  if ((newInt & 0x00008000) > 0) {
				newInt |= 0xFFFF0000;
		  } else {
				newInt &= 0x0000FFFF;
		  }
		  return newInt;
	 }
  
	 int copyDataPacketTo(DataPacket_ADS1299 target) {
		  isNewDataPacketAvailable = false;
		  dataPacket.copyTo(target);
		  return 0;
	 }
  
	 //  int measurePacketLength() {
	 //    
	 //    //assume curBuffIndex has already been incremented to the next open spot
	 //    int startInd = curBuffIndex-1;
	 //    int endInd = curBuffIndex-1;
	 //
	 //    //roll backwards to find the start of the packet
	 //    while ((startInd >= 0) && (serialBuff[startInd] != BYTE_START)) {
	 //      startInd--;
	 //    }
	 //    if (startInd < 0) {
	 //      //didn't find the start byte..so ignore this data packet
	 //      return 0;
	 //    } else if ((endInd - startInd + 1) < 3) {
	 //      //data packet isn't long enough to hold any data...so ignore this data packet
	 //      return 0;
	 //    } else {
	 //      //int n_bytes = int(serialBuff[startInd + 1]); //this is the number of bytes in the payload
	 //      //System.out.println("OpenBCI_ADS1299: measurePacketLength = " + (endInd-startInd+1));
	 //      return endInd-startInd+1;
	 //    }
	 //  }
      
    
};


class DataPacket_ADS1299 {
	 int sampleIndex;
	 int[] values;

	 //constructor, give it "nValues", which should match the number of values in the
	 //data payload in each data packet from the Arduino.  This is likely to be at least
	 //the number of EEG channels in the OpenBCI system (ie, 8 channels if a single OpenBCI
	 //board) plus whatever auxiliary data the Arduino is sending. 
	 DataPacket_ADS1299(int nValues) {
		  values = new int[nValues];
	 }

	 int printToConsole() {
		  System.out.print("printToConsole: DataPacket = ");
		  System.out.print(sampleIndex);
		  for (int i=0; i < values.length; i++) {
				System.out.print(", " + values[i]);
		  }
		  System.out.println();
		  return 0;
	 }

	 int copyTo(DataPacket_ADS1299 target) {
		  target.sampleIndex = sampleIndex;
		  //int nvalues = min(values.length, target.values.length); //handles case when nchan < OpenBCI_nchannels
		  int nvalues = values.length;
		  for (int i=0; i < nvalues; i++) {
				target.values[i] = values[i];
		  }
		  return 0;
	 }
};

class DataStatus {
	 public boolean is_railed;
	 private int threshold_railed;
	 public boolean is_railed_warn;
	 private int threshold_railed_warn;
  
	 DataStatus(int thresh_railed, int thresh_railed_warn) {
		  is_railed = false;
		  threshold_railed = thresh_railed;
		  is_railed_warn = false;
		  threshold_railed_warn = thresh_railed_warn;
	 }
	 public void update(int data_value) {
		  is_railed = false;
		  if (Math.abs(data_value) >= threshold_railed) is_railed = true;
		  is_railed_warn = false;
		  if (Math.abs(data_value) >= threshold_railed_warn) is_railed_warn = true;
	 }
};
