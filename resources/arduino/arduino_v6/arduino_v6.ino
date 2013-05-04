#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>
#include <Servo.h> 
#include <Log.h>
#include <PID_v1.h>

//////////////////////////////////////////
////// Constants
//////////////////////////////////////////
#define DEBUG                       true

#define MOTOR_1_PWM_PIN             2
#define MOTOR_2_PWM_PIN             3
#define MOTOR_3_PWM_PIN             4
#define MOTOR_4_PWM_PIN             5

#define COMMAND_CONTROL             1

#define ACTION_LEFT_STICK           1
#define ACTION_RIGHT_STICK          2

#define PID_SAMPLE_TIME_MILLIS     10

#define INPUT_MIN                -100
#define INPUT_MAX                 100

#define THROTTLE_MIN               50
#define THROTTLE_MAX              180

#define ROLL_MIN                  -45
#define ROLL_MAX                   45

#define YAW_MIN                   -90
#define YAW_MAX                    90

#define PITCH_MIN                 -45
#define PITCH_MAX                  45 
//////////////////////////////////////////
////// Members
//////////////////////////////////////////
Log *_log;
  
// members-motors
Servo _motor1;
Servo _motor2;
Servo _motor3;
Servo _motor4;

typedef struct _Control {
  uint8_t throttle;
  int8_t roll;
  int8_t yaw;
  int8_t pitch;
}Control;


Control _control;

double _rollSetPoint;     // given input
double _rollInput;        // current value
double _rollOutput;       // calculated output

double _yawSetPoint;
double _yawInput;
double _yawOutput;

double _pitchSetPoint;
double _pitchInput;
double _pitchOutput;

PID _roll(&_rollSetPoint, &_rollInput, &_rollOutput, 1, 0, 0, DIRECT);
PID _yaw(&_yawSetPoint, &_yawInput, &_yawOutput, 1, 0, 0, DIRECT);
PID _pitch(&_pitchSetPoint, &_pitchInput, &_pitchOutput, 1, 0, 0, DIRECT);

//////////////////////////////////////////
////// Initialization
//////////////////////////////////////////

/**
 * Called once when the Arduino first loads or resets 
 */
void onCreate() {
  _log = new Log(DEBUG); 
  
  _motor1.attach(MOTOR_1_PWM_PIN);
  _motor2.attach(MOTOR_2_PWM_PIN);
  _motor3.attach(MOTOR_3_PWM_PIN);
  _motor4.attach(MOTOR_4_PWM_PIN);
  
  _control.throttle = THROTTLE_MIN;
  _control.roll = 0;
  _control.yaw = 0;
  _control.pitch = 0;
  
  _roll.SetOutputLimits(ROLL_MIN, ROLL_MAX);
  _roll.SetSampleTime(PID_SAMPLE_TIME_MILLIS);
  
  _yaw.SetOutputLimits(YAW_MIN, YAW_MAX);
  _yaw.SetSampleTime(PID_SAMPLE_TIME_MILLIS);

  _pitch.SetOutputLimits(PITCH_MIN, PITCH_MAX);
  _pitch.SetSampleTime(PID_SAMPLE_TIME_MILLIS);  
}

//////////////////////////////////////////
////// Events
//////////////////////////////////////////

/**
 * Handle messages sent from the Android device
 *
 * @param command The command sent by the Android device
 * @param action The action sent by the Android device
 * @param dataLength The length of "data"
 * @param data Pointer to the extra data sent by the device
 */
void onMessageReceived(byte command, byte action, byte dataLength, byte* data) {
  switch (command) {
     case COMMAND_CONTROL:
        _log->d("Command Control");
        onCommandControl(action, dataLength, data);
        break; 
  }
}

/**
 * Handle control requests
 *
 * @param action The action sent by the Android device
 * @param dataLength The length of "data"
 * @param data Pointer to the extra data sent by the device
 */
void onCommandControl(byte action, byte dataLength, byte* data) {
  switch(action) {
    case ACTION_LEFT_STICK: {
      _log->d("Action Left Stick");
      int targetThrottle = (int8_t) data[0];
      int targetYaw = (int8_t) data[1];
      
      if (targetThrottle < 0) {
        _log->d("Negative throttle detected, rounding to zero");
        targetThrottle = 0;
      }
      
      _log->d("Raw Throttle: ", targetThrottle);
      _log->d("Raw Yaw: ", targetYaw);
      
      targetThrottle = map(targetThrottle, 0, INPUT_MAX, THROTTLE_MIN, THROTTLE_MAX);
      targetYaw = map(targetYaw, INPUT_MIN, INPUT_MAX, YAW_MIN, YAW_MAX);
      
      _log->d("Throttle: ", targetThrottle);
      _log->d("Yaw: ", targetYaw);
      
      _control.throttle = targetThrottle;
      _control.yaw = targetYaw;
      break;
    }
    
    case ACTION_RIGHT_STICK: {
      _log->d("Action Right Stick");
      int8_t targetPitch = (int8_t) data[0];
      int8_t targetRoll = (int8_t) data[1];
      
      _log->d("Raw Pitch: ", targetPitch);
      _log->d("Raw Roll: ", targetRoll);
      
      targetPitch = map(targetPitch, INPUT_MIN, INPUT_MAX, PITCH_MIN, PITCH_MAX);
      targetRoll = map(targetRoll, INPUT_MIN, INPUT_MAX, ROLL_MIN, ROLL_MAX);
      
      _log->d("Pitch: ", targetPitch);
      _log->d("Roll: ", targetRoll);
      
      _control.pitch = targetPitch;
      _control.roll = targetRoll;
      break;
    }
  }     
}


//////////////////////////////////////////
////// Main loop
//////////////////////////////////////////

/**
 * Main loop. 
 * This method is called very frequently in an infinite loop
 */
void onLoop() {
  // calculate true angles from accelerometer, gyroscope and magnetometer
  int16_t* angles = getAngles();
  _rollInput = (double) angles[0];
  _yawInput = (double) angles[1];
  _pitchInput = (double) angles[2];
  
  // set target values
  _rollSetPoint = _control.roll;
  _yawSetPoint = _control.yaw;
  _pitchSetPoint = _control.pitch;  
  
  // let the PID calculate the output from defined variables: xxxInput & xxxSetPoint.
  // output will be saved at xxxOutput
  _roll.Compute();
  _yaw.Compute();
  _pitch.Compute();

  // now that we have all the values, apply these values to all our motors
  int motor1Power = _control.throttle + _pitchOutput + _yawOutput;
  int motor2Power = _control.throttle + _rollOutput  - _yawOutput;
  int motor3Power = _control.throttle - _pitchOutput + _yawOutput;
  int motor4Power = _control.throttle - _rollOutput  - _yawOutput;
  
  _motor1.write(motor1Power);
  _motor2.write(motor2Power);
  _motor3.write(motor3Power);
  _motor4.write(motor4Power);
  
  
  _log->d("Motor 1 Power: ", motor1Power);
  _log->d("Motor 2 Power: ", motor2Power);
  _log->d("Motor 3 Power: ", motor3Power);
  _log->d("Motor 4 Power: ", motor4Power);
}










////
/////// Read any futher only if curious...
////



/**
 * Read angles from all the following sensors: accelerometer, gyroscope and magnetometer
 *
 * @return 0:Roll, 1:Yaw, 2:Pitch
 */
int16_t* getAngles() {


  // TODO get angles: Roll, Yaw and Pitch



}




















//////////////////////////////////////////
////// Android communication boilerplate 
//////////////////////////////////////////
#define BUFFER_SIZE            16
#define TIME_STEP_BETWEEN_USB_RECONNECTIONS 1000 // in milliseconds

const char *USB_MANUFACTURER = "Amir Lazarovich";
const char *USB_MODEL        = "HeliDroid";
const char *USB_DESCRIPTION  = "Controller for Android powered QuadCopter";
const char *USB_VERSION      = "1.0";
const char *USB_SITE         = "http://www.helidroid.com";
const char *USB_SERIAL       = "0000000000000001";

                    
// command (1 byte), action (1 byte), data-length (1 byte), data (X bytes) 
AndroidAccessory *_acc;

// members-states
long _lastTimeReconnectedToUsb;



/**
 * Called once when the arduino first loads (or resets)
 */
void setup(){
  Serial.begin(9600);
  _acc = new AndroidAccessory(USB_MANUFACTURER,
                              USB_MODEL,
                              USB_DESCRIPTION,
                              USB_VERSION,
                              USB_SITE,
                              USB_SERIAL);
  _acc->powerOn();
  _lastTimeReconnectedToUsb = 0;
  
  onCreate();
}

/**
 * loop forever. 
 * Arduino calls this method in an infinite loop after returning from method "setup"
 */
void loop() {  
  if (_acc->isConnected()) {
    //Serial.println("reading...");   
    
    byte msg[BUFFER_SIZE];
    int len = _acc->read(msg, BUFFER_SIZE, 1);
    if (len > 0) {
      Serial.print("read: ");
      Serial.print(len, DEC);
      Serial.println(" bytes");

      handleMsgFromDevice(msg);
      sendAck();
    }
  } else if (_lastTimeReconnectedToUsb + TIME_STEP_BETWEEN_USB_RECONNECTIONS < millis()) {
    Serial.println("USB is not connected. Trying to reconnect...");
    reconnectUsb();
    _lastTimeReconnectedToUsb = millis();
  }
  
  onLoop();
}

/**
 * Handle messages coming from the Android device
 *
 * @param msg The raw payload 
 */
void handleMsgFromDevice(byte* msg) {
  byte command = msg[0];
  byte action = msg[1];
  byte dataLength = msg[2];
  printValues(command, action, dataLength);
  onMessageReceived(command, action, dataLength, msg + 3); 
}

/**
 * Try to reconnect to the Android device
 */
void reconnectUsb() {
  delete _acc;
  _acc = new AndroidAccessory(USB_MANUFACTURER,
                              USB_MODEL,
                              USB_DESCRIPTION,
                              USB_VERSION,
                              USB_SITE,
                              USB_SERIAL);
  _acc->powerOn();
}    

/**
 * Send acknowledge to connected Android device
 */ 
void sendAck() {
  if (_acc->isConnected()) {
    byte msg[1];
    msg[0] = 1;
    _acc->write(msg, 1);
  }  
}

/**
 * Print the command, action and data length to serial port 
 *
 * @param command The command sent by the Android device
 * @param action The action sent by the Android device
 * @param dataLength The length of the appended data field
 */
void printValues(byte command, byte action, byte dataLength) {
  Serial.print("Command: ");
  Serial.print(command, DEC);
  Serial.print(". Action: ");
  Serial.print(action, DEC);
  Serial.print(" Data Length: ");
  Serial.println(dataLength, DEC);
}
