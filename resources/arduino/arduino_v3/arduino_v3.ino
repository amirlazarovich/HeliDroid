#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>
#include <Servo.h> 
#include <Log.h>

//////////////////////////////////////////
////// Constants
//////////////////////////////////////////
#define DEBUG true

#define BUFFER_SIZE 4

#define COMMAND_STAND_BY 1
#define COMMAND_MOTORS   2
#define COMMAND_ROTATE   3

#define ACTION_MOTOR_POWER       1
#define ACTION_ROLL       2
#define ACTION_YAW   3
#define ACTION_PITCH      4
#define ACTION_ON                5
#define ACTION_OFF               6

#define BASE_ANGLE     90

#define TIME_STEP_BETWEEN_USB_RECONNECTIONS 1000 // in milliseconds

#define INPUT_MIN_POWER 0
#define INPUT_MAX_POWER 100
#define MOTOR_MIN_POWER 50
#define MOTOR_MAX_POWER 180
#define STOP_MOTOR      0

#define MOTOR_1_PWM_PIN         2
#define MOTOR_2_PWM_PIN         3
#define MOTOR_3_PWM_PIN         4
#define MOTOR_4_PWM_PIN         5

const char *USB_MANUFACTURER = "Amir Lazarovich";
const char *USB_MODEL        = "HeliDroid";
const char *USB_DESCRIPTION  = "Controller for Android powered QuadCopter";
const char *USB_VERSION      = "1.0";
const char *USB_SITE         = "http://www.helidroid.com";
const char *USB_SERIAL       = "0000000012345678";


//////////////////////////////////////////
////// Members
//////////////////////////////////////////
// command (1 byte), action (1 byte), data (X bytes) 
AndroidAccessory *_acc;
Log *_log;
  
// members-motors
Servo _motor1;
Servo _motor2;
Servo _motor3;
Servo _motor4;

// members-rotation
uint8_t _angle;

// members-states
long _lastTimeReconnectedToUsb;
int _lastMotorPower;

//////////////////////////////////////////
////// Initialization
//////////////////////////////////////////
/**
 * Setup HeliDroid firmware
 */
void setup(){
  Serial.begin(9600);
  initMotors();
  initMembers();
  
  _acc->powerOn();
}

/**
 * Initialize all motors
 */
void initMotors() {
  _motor1.attach(MOTOR_1_PWM_PIN);
  _motor2.attach(MOTOR_2_PWM_PIN);
  _motor3.attach(MOTOR_3_PWM_PIN);
  _motor4.attach(MOTOR_4_PWM_PIN);
}


/**
 * Initialize all members
 */
void initMembers() {
  _lastTimeReconnectedToUsb = 0;
  _lastMotorPower = 0;
  _angle = BASE_ANGLE;
  _acc = new AndroidAccessory(USB_MANUFACTURER,
                              USB_MODEL,
                              USB_DESCRIPTION,
                              USB_VERSION,
                              USB_SITE,
                              USB_SERIAL);
  _log = new Log(DEBUG);                               
}

//////////////////////////////////////////
////// Main loop
//////////////////////////////////////////

/**
 * loop forever
 */
void loop() {  
  if (_acc->isConnected()) {
    _log->d("reading...");
   
    byte msg[BUFFER_SIZE];
    int len = _acc->read(msg, BUFFER_SIZE);
    if (len > 0) {
      _log->d("read: ", len, " bytes");      
      handleMsgFromDevice(msg);
      sendAck();
    }
  } else if (_lastTimeReconnectedToUsb + TIME_STEP_BETWEEN_USB_RECONNECTIONS < millis()) {
    _log->d("USB is not connected. Trying to re-connected");
    reconnectUsb();
    _lastTimeReconnectedToUsb = millis();
  }
}

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



//////////////////////////////////////////
////// Events
//////////////////////////////////////////
/**
 * Handle messages from the Android device
 *
 * @param msg
 */
void handleMsgFromDevice(byte* msg) {
  byte command = msg[0];
  byte action = msg[1];
  
  switch (command) {
    case COMMAND_STAND_BY:
      _log->d("Settings motors in standby: ", action);
      if (action == ACTION_ON) {
          // stop motors
          _motor1.write(STOP_MOTOR);
          _motor2.write(STOP_MOTOR);
          _motor3.write(STOP_MOTOR);
          _motor4.write(STOP_MOTOR);
      } else {
          // resume motors' speed
          _motor1.write(_lastMotorPower);
          _motor2.write(_lastMotorPower);
          _motor3.write(_lastMotorPower);
          _motor4.write(_lastMotorPower);
      }
      
      break;
      
    case COMMAND_MOTORS:
      _log->d("Controlling all motors");
      
      handleActionOnMotors(action, msg + 2);
      break; 
      
    case COMMAND_ROTATE:
      _log->d("Controlling Servo rotation angle");
      
      handleRotation(action, msg + 2);
      break;
    
    default:
      _log->d("Unknown command from Android device: ", command);
  }  
}


//////////////////////////////////////////
////// Private
//////////////////////////////////////////
/**
 * Handle rotation requests
 * 
 * @param action
 * @param data
 */
void handleRotation(byte action, byte *data) {
  int8_t value = (int8_t) data[0];
 
  switch (action) {
    case ACTION_ROLL: {
      _log->d("Event: Orientation: ", value);
      
      break;
    }
      
    case ACTION_PITCH: {
      _log->d("Event: Tilt Up / Down: ", value);
      
      break;
    }
    
    case ACTION_YAW: {
      _log->d("Event: Tilt Left / Right: ", value);
      
      break;
    }
  }  
}

/**
 * Handle motor actions requests
 * 
 * @param action
 * @param data
 */
void handleActionOnMotors(byte action, byte *data) {
  switch (action) {
      case ACTION_MOTOR_POWER: {
        int throttle = (int8_t) data[0];
        _log->d("Throttle:: ", throttle);
        
        if (throttle < 0) {
          throttle = 0;
        } 
        m
        throttle = translate(throttle);        
        _log->dp("Thro:: direction: ", reverse, "Reverse", "Forward");
        _log->d(", Power: ", power);
           
        // TODO: need to support directions
//        _motor1.write(power);
//        _motor2.write(power);
//        _motor3.write(power);
//        _motor4.write(power);
          
        // keep last power
        _lastMotorPower = power;
        break;
      }
    }
}

/**
 * Translate input value to the motor speed range
 *
 * @param value The value that should be translated
 */ 
int translate(int value) {
  return translate(value, INPUT_MIN_POWER, INPUT_MAX_POWER, MOTOR_MIN_POWER, MOTOR_MAX_POWER);
}

/**
 * Translate input value in given direction
 * 
 * @param value The value that should be translated
 * @param leftMin
 * @param leftMax
 * @param rightMin  
 * @param rightMax
 */
int translate(int value, int leftMin, int leftMax, int rightMin, int rightMax) {
    // Figure out how 'wide' each range is
    int leftSpan = leftMax - leftMin;
    int rightSpan = rightMax - rightMin;

    // Convert the left range into a 0-1 range (float)
    float valueScaled = (value - leftMin) / (float) leftSpan;

    // Convert the 0-1 range into a value in the right range.
    return rightMin + (valueScaled * rightSpan);
}

/**
 * Send acknowledge to connected Android device
 */ 
void sendAck() {
  if (_acc->isConnected()) 
  {
    byte msg[1];
    msg[0] = 1;
    _acc->write(msg, 1);
  }  
}
