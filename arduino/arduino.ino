#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>
#include <Servo.h> 
#include <AFMotor.h>

#define DEBUG true
#define ON 1
#define OFF 0
#define LEFT 1
#define RIGHT 0

#define MOTOR_1  1
#define MOTOR_2  2
#define STAND_BY 3
#define ROTATE   4

#define ACTION_POWER_ON        1
#define ACTION_POWER_OFF       2
#define ACTION_ROTATE_BY_ANGLE 3
#define ACTION_RESET_ROTATION  4
#define ACTION_START_ROTATE    5
#define ACTION_END_ROTATE      6

#define BASE_ANGLE 90
#define ROTATION_STEP 1
#define MAX_ANGLE 180
#define MIN_ANGLE 0

#define ROTATION_STEP_TIME_DELAY 6
#define TIME_STEP_BETWEEN_USB_RECONNECTIONS 1000 // in milliseconds

#define INPUT_MIN_SPEED 0
#define INPUT_MAX_SPEED 255
#define MOTOR_MIN_SPEED 99
#define MOTOR_MAX_SPEED 133
#define STOP_MOTOR      0

#define ROTATING_SERVO_PWM_PIN  8
#define MOTOR_1_PWM_PIN         9
#define MOTOR_2_PWM_PIN         10

// command (1 byte), action (1 byte), data (X bytes)


AndroidAccessory acc("ZepDroid",
                     "Geekcon",
                     "Flying machine",
                     "1.0",
                     "http://www.zepdroid.com",
                     "0000000012345678"); // Serial number
                     
typedef struct _RotationState {
  int direction;
  boolean isRotating;  
  unsigned long timestamp;
}RotationState;

// Motors
Servo _motor1;
Servo _motor2;

// Rotating Servo
Servo _servoRotator;
uint8_t _angle;
RotationState _rotationState;

// states
long _lastTimeReconnectedToUsb;
int _lastMotorSpeed;



void setup(){
  Serial.begin(9600);
  acc.powerOn();
  
  initMotors();
  initServo();
  initMembers();
}

void initMembers() {
  _rotationState.isRotating = false;
  _rotationState.timestamp = 0;  
  _lastTimeReconnectedToUsb = 0;
  _lastMotorSpeed = 0;
}

void initMotors() {
  _motor1.attach(MOTOR_1_PWM_PIN);
  _motor2.attach(MOTOR_2_PWM_PIN);
}

void initServo() {
  _servoRotator.attach(ROTATING_SERVO_PWM_PIN);
  _angle = BASE_ANGLE;
  _servoRotator.write(_angle);
}

void loop(){
  // Incoming data from Android device.
  byte msg[1];
  
  if (acc.isConnected()) 
  {
    // The reading from Android code
    int len = acc.read(msg, 4, 1);
    
    if (len > 0) {
      handleMsgFromDevice(msg);
      sendAck();
    }
  } else if (_lastTimeReconnectedToUsb + TIME_STEP_BETWEEN_USB_RECONNECTIONS < millis()) {
    Serial.println("USB is not connected. Trying to re-connected");
    reconnectToUsb();
    _lastTimeReconnectedToUsb = millis();
  }
  
  processEvents();
}

void reconnectToUsb() { 
  acc.powerOn();                     
}


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
    
    // do the actual rotation
    _servoRotator.write(_angle);
    
    // prepare next rotation step    
    _rotationState.timestamp = millis() + ROTATION_STEP_TIME_DELAY;
  }  
}

void handleMsgFromDevice(byte* msg) {
  byte command = msg[0];
  byte action = msg[1];
  
  int input0;
  int input1;
  int pwm;
  boolean handled = true;
  switch (command) {
    case MOTOR_1:
      if (DEBUG) Serial.println("Controlling Motor 1");
      
      handleActionOnMotor(action, msg + 2, _motor1);
      break;
      
    case MOTOR_2:
      if (DEBUG) Serial.println("Controlling Motor 2");
      
      handleActionOnMotor(action, msg + 2, _motor2);
      break; 
       
    case STAND_BY:
      if (DEBUG) {
        Serial.print("Settings motors in standby: ");
        Serial.println(action, DEC); 
      }
      
      if (action == ON) {
          // stop motors
          _motor1.write(STOP_MOTOR);
          _motor2.write(STOP_MOTOR);
      } else {
          // resume motors' speed
          _motor1.write(_lastMotorSpeed);
          _motor2.write(_lastMotorSpeed);
      }
      
      break;
      
    case ROTATE:
      if (DEBUG) Serial.println("Controlling Servo rotation angle");
      handleRotation(action, msg + 2);
      break;
    
    default:
      if (DEBUG) Serial.println("Unknown command from Android device: " + command);
      handled = false;
  }  
}


void handleRotation(byte action, byte *data) {
  switch (action) {
    case ACTION_ROTATE_BY_ANGLE: {
      uint8_t angle = (uint8_t) data[0]; 
      if (DEBUG) {
         Serial.print("Rotating servo to angle: ");
         Serial.println(angle, DEC); 
      }
      
      _angle = angle;
      _servoRotator.write(angle); 
      break; 
    }
    
    case ACTION_START_ROTATE: {
      if (DEBUG) Serial.println("Starting rotation");
      uint8_t direction = data[0];
      _rotationState.isRotating = true;
      _rotationState.direction = direction; 
      break;
    }
      
    case ACTION_END_ROTATE: {
      if (DEBUG) Serial.println("Stopping rotation");
      _rotationState.isRotating = false;
      break;
    }
    
    case ACTION_RESET_ROTATION: {
      if (DEBUG) Serial.println("Resting rotating servo");
      _angle = BASE_ANGLE;
      _servoRotator.write(BASE_ANGLE);
      break;
    }
  }  
}

void handleActionOnMotor(byte action, byte *data, Servo motor) {
  switch (action) {
      case ACTION_POWER_ON: {
          byte direction = data[0];
          int speed = translate((int) data[1]);
          
          if (DEBUG) {
            Serial.print("Powering ON: direction: ");
            Serial.print(direction, DEC);
            Serial.print(", Speed: ");
            Serial.println(speed, DEC); 
          }
           
          // TODO: need to support directions
          motor.write(speed);
          
          // keep last speed
          _lastMotorSpeed = speed;
        }
        
        break;
        
      case ACTION_POWER_OFF: {
          motor.write(STOP_MOTOR);
        }
        
        break;
    }
}

/**
 * translate input value to the motor speed range
 */ 
int translate(int value) {
  return translate(value, INPUT_MIN_SPEED, INPUT_MAX_SPEED, MOTOR_MIN_SPEED, MOTOR_MAX_SPEED);
}

int translate(int value, int leftMin, int leftMax, int rightMin, int rightMax) {
    // Figure out how 'wide' each range is
    int leftSpan = leftMax - leftMin;
    int rightSpan = rightMax - rightMin;

    // Convert the left range into a 0-1 range (float)
    float valueScaled = (value - leftMin) / (float) leftSpan;

    // Convert the 0-1 range into a value in the right range.
    return rightMin + (valueScaled * rightSpan);
}

void sendAck() {
  if (acc.isConnected()) 
  {
    byte msg[1];
    msg[0] = 1;
    acc.write(msg, 1);
  }  
}
