#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>
#include <Servo.h> 
#include <Log.h>

//////////////////////////////////////////
////// Constants
//////////////////////////////////////////
#define DEBUG                       true
#define MOTOR_1_PWM_PIN             2
#define MOTOR_2_PWM_PIN             3
#define MOTOR_3_PWM_PIN             4
#define MOTOR_4_PWM_PIN             5

#define COMMAND_CONTROL             1

#define ACTION_THROTTLE             1
#define ACTION_ROLL                 2
#define ACTION_YAW                  3
#define ACTION_PITCH                4

#define INPUT_MIN                   0
#define INPUT_MAX                 100
#define MOTOR_MIN                  50
#define MOTOR_MAX                 180

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
  int8_t throttle;
  int8_t roll;
  int8_t yaw;
  int8_t pitch;
}Control;

Control _control;


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
  
  _control.throttle = MOTOR_MIN;
  _control.roll = 0;
  _control.yaw = 0;
  _control.pitch = 0;
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
    case ACTION_THROTTLE:
      _log->d("Action Throttle");
      _control.throttle = (int8_t) data[0];
      
      if (_control.throttle < 0) {
        _log->d("Negative throttle detected, rounding to zero");
        _control.throttle = 0;
      }
      
      _log->d("Raw Throttle: ", _control.throttle);
      _control.throttle = map(_control.throttle, INPUT_MIN, INPUT_MAX, MOTOR_MIN, MOTOR_MAX);
      _log->d("Throttle: ", _control.throttle);
      break;
      
    case ACTION_ROLL:
      _log->d("Action Roll");
      _control.roll = (int8_t) data[0];
      _log->d("Raw roll: ", _control.roll);
      _control.roll = map(_control.roll, INPUT_MIN, INPUT_MAX, MOTOR_MIN, MOTOR_MAX);
      _log->d("roll: ", _control.roll);
      break;
      
    case ACTION_YAW:
      _log->d("Action Yaw");
      _control.yaw = (int8_t) data[0];
      _log->d("Raw yaw: ", _control.yaw);
      _control.yaw = map(_control.yaw, INPUT_MIN, INPUT_MAX, MOTOR_MIN, MOTOR_MAX);
      _log->d("yaw: ", _control.yaw);
      break;
      
      
    case ACTION_PITCH:
      _log->d("Action Pitch");
      _control.pitch = (int8_t) data[0];
      _log->d("Raw pitch: ", _control.pitch);
      _control.pitch = map(_control.pitch, INPUT_MIN, INPUT_MAX, MOTOR_MIN, MOTOR_MAX);
      _log->d("pitch: ", _control.pitch);
      break;
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
  int motor1Power = _control.throttle + _control.pitch + _control.yaw;
  int motor2Power = _control.throttle + _control.roll  - _control.yaw;
  int motor3Power = _control.throttle - _control.pitch + _control.yaw;
  int motor4Power = _control.throttle - _control.roll  - _control.yaw;
  _motor1.write(motor1Power);
  _motor2.write(motor2Power);
  _motor3.write(motor3Power);
  _motor4.write(motor4Power);
}










////
/////// Read any futher only if curious...
////























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
    Serial.println("reading...");
   
    byte msg[BUFFER_SIZE];
    int len = _acc->read(msg, BUFFER_SIZE);
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
  if (_acc->isConnected()) 
  {
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
