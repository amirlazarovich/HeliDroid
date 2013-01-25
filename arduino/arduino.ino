#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>
#include <Servo.h> 
#include <AFMotor.h>

#define DEBUG true
#define ON     1
#define OFF    0
#define LEFT   1
#define RIGHT  0

#define COMMAND_STAND_BY 1
#define COMMAND_MOTORS   2
#define COMMAND_ROTATE   3

#define ACTION_MOTOR_POWER       1
#define ACTION_ORIENTATION       2
#define ACTION_TILT_LEFT_RIGHT   3
#define ACTION_TILT_UP_DOWN      4

#define BASE_ANGLE     90
#define ROTATION_STEP   1
#define MAX_ANGLE     180
#define MIN_ANGLE       0

#define ROTATION_STEP_TIME_DELAY               6
#define TIME_STEP_BETWEEN_USB_RECONNECTIONS 1000 // in milliseconds

#define INPUT_MIN_POWER 0
#define INPUT_MAX_POWER 100
#define MOTOR_MIN_POWER 99
#define MOTOR_MAX_POWER 133
#define STOP_MOTOR      0

#define MOTOR_1_PWM_PIN         9
#define MOTOR_2_PWM_PIN         10
#define MOTOR_3_PWM_PIN         11
#define MOTOR_4_PWM_PIN         12

// command (1 byte), action (1 byte), data (X bytes)
 
AndroidAccessory acc("HeliDroid",
                     "LAbs",
                     "Flying machine",
                     "1.0",
                     "http://www.helidroid.com",
                     "0000000012345678"); // Serial number
                     
typedef struct _RotationState {
  int direction;
  boolean isRotating;  
  unsigned long timestamp;
}RotationState;

// Motors
Servo _motor1;
Servo _motor2;
Servo _motor3;
Servo _motor4;

// Rotation
uint8_t _angle;
RotationState _rotationState;

// states
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
  acc.powerOn();
  
  initMotors();
  initMembers();
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
  _rotationState.isRotating = false;
  _rotationState.timestamp = 0;  
  _lastTimeReconnectedToUsb = 0;
  _lastMotorPower = 0;
  _angle = BASE_ANGLE;
}

//////////////////////////////////////////
////// Main loop
//////////////////////////////////////////
/**
 * loop forever
 */
void loop(){
  // Incoming data from Android device.
  int bufferSize = 1024;
  byte msg[bufferSize];
  
  if (acc.isConnected()) 
  {
    // The reading from Android code
    if (DEBUG) Serial.println("reading...");
    int offset = 0;
    int numOfBytes = 4;
    int chunksRead = 0;
    int len = 0;
    do {
      len = acc.read(msg + offset, numOfBytes);  
      offset += numOfBytes;
      
      if (len > 0) {
        chunksRead++;
      }
      
      if (DEBUG) {
        Serial.print("read: ");
        Serial.print(len, DEC);
        Serial.println(" bytes");        
      }
    } while (len > 0 && offset <= bufferSize - numOfBytes);
    
      
    if (DEBUG) {
      Serial.print("chunks read: ");
      Serial.println(chunksRead, DEC);
    }
      
    int chunksOffset = 0;
    while (chunksRead > 0) {
      handleMsgFromDevice(msg + chunksOffset);
      sendAck();
      
      chunksOffset += numOfBytes;
      chunksRead--;
      
      if (DEBUG) {
        Serial.print("chunks offset: ");
        Serial.print(chunksOffset, DEC);
        Serial.print(", chunks left: ");
        Serial.println(chunksRead, DEC);
      } 
    } 
  } else if (_lastTimeReconnectedToUsb + TIME_STEP_BETWEEN_USB_RECONNECTIONS < millis()) {
    Serial.println("USB is not connected. Trying to re-connected");
    reconnectToUsb();
    _lastTimeReconnectedToUsb = millis();
  }
  
  processEvents();
}


//////////////////////////////////////////
////// Events
//////////////////////////////////////////
/**
 * Process events on each loop cycle
 */
void processEvents() {
  if (_rotationState.isRotating && _rotationState.timestamp < millis()) {
    if (DEBUG) Serial.println("Auto rotating");
    if (_rotationState.direction == LEFT && ((_angle + ROTATION_STEP) <= MAX_ANGLE)) {
       // rotate left
       if (DEBUG) Serial.println("Rotating left");
       _angle += ROTATION_STEP;
    } else if (_rotationState.direction == RIGHT && ((_angle - ROTATION_STEP) >= MIN_ANGLE)) {
       // rotate right
       if (DEBUG) Serial.println("Rotating right");
       _angle -= ROTATION_STEP;
    }
        
    if (_angle >= MAX_ANGLE || _angle <= MIN_ANGLE) {
      // end rotation
      if (DEBUG) Serial.println("End rotation");
      
      if (_angle >= MAX_ANGLE) {
        _angle = MAX_ANGLE;  
      } else if (_angle <= MIN_ANGLE) {
        _angle = MIN_ANGLE;
      }
      
      _rotationState.isRotating = false;
    }



    
    // TODO do the actual rotation using the calculated _angle



    
    // prepare next rotation step    
    _rotationState.timestamp = millis() + ROTATION_STEP_TIME_DELAY;
  }  
}

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
      if (DEBUG) {
        Serial.print("Settings motors in standby: ");
        Serial.println(action, DEC); 
      }
      
      if (action == ON) {
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
      if (DEBUG) Serial.println("Controlling all motors");
      
      handleActionOnMotors(action, msg + 2);
      break; 
      
    case COMMAND_ROTATE:
      if (DEBUG) Serial.println("Controlling Servo rotation angle");
      
      handleRotation(action, msg + 2);
      break;
    
    default:
      if (DEBUG) {
        Serial.print("Unknown command from Android device: ");
        Serial.println(command, DEC);
      }
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
  switch (action) {
    case ACTION_ORIENTATION: {
      int8_t value = (int8_t) data[0];
      if (DEBUG) {
        Serial.print("Event: Orientation: ");
        Serial.println(value, DEC);
      }
      
      break;
    }
      
    case ACTION_TILT_UP_DOWN: {
      int8_t value = (int8_t) data[0];
      if (DEBUG) {
        Serial.print("Event: Tilt Up / Down: ");
        Serial.println(value, DEC);
      }
      
      break;
    }
    
    case ACTION_TILT_LEFT_RIGHT: {
      int8_t value = (int8_t) data[0];
      if (DEBUG) {
        Serial.print("Event: Tilt Left / Right: ");
        Serial.println(value, DEC);
      }
      
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
        int8_t power = (int8_t) data[0];
        if (DEBUG) {
           Serial.print("Raw Power value: ");
           Serial.println(power, DEC); 
        }
        
        boolean isReversingMotors;
        if (power < 0) {
          isReversingMotors = true;
          power = -power;
        } else {
          isReversingMotors = false;
        }
        
        power = translate(power);        

        if (DEBUG) {
          Serial.print("Power: direction: ");
          if (isReversingMotors) {
            Serial.print("Reverse");
          } else {
            Serial.print("Forward");
          }
          
          Serial.print(", Power: ");
          Serial.println(power, DEC); 
        }
           
        // TODO: need to support directions
        _motor1.write(power);
        _motor2.write(power);
        _motor3.write(power);
        _motor4.write(power);
          
        // keep last power
        _lastMotorPower = power;
        break;
      }
    }
}

/**
 * Try to reconnect to the USB port
 */
void reconnectToUsb() { 
  acc.powerOn();                     
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
  if (acc.isConnected()) 
  {
    byte msg[1];
    msg[0] = 1;
    acc.write(msg, 1);
  }  
}
