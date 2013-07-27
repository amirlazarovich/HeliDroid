#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>
#include <Servo.h> 
#include <Log.h>
#include <PID_v1.h>
#include <Wire.h>
#include <L3G.h>
#include <ADXL345.h>

//////////////////////////////////////////
////// Constants
//////////////////////////////////////////
#define DEBUG                       false

#define DEFAULT_STANDBY             true

#define MOTOR_1_PWM_PIN             2
#define MOTOR_2_PWM_PIN             3
#define MOTOR_3_PWM_PIN             4
#define MOTOR_4_PWM_PIN             5
#define LED_1_PIN                  13

#define COMMAND_CONTROL             1
#define COMMAND_SETTINGS            2
#define COMMAND_GET                 3
#define COMMAND_ACK                 4
#define COMMAND_RESPONSE            5

#define ACTION_STICKS               3
#define ACTION_STANDBY              4
#define ACTION_TUNE                 5

#define TUNE_PITCH                  1
#define TUNE_ROLL                   2
#define TUNE_YAW                    3

#define PID_SAMPLE_TIME_MILLIS     10

#define INPUT_MIN                -100
#define INPUT_MAX                 100

#define THROTTLE_MIN               50
#define THROTTLE_MAX              180

#define ROLL_MIN                  -30.0
#define ROLL_MAX                   30.0

#define YAW_MIN                   -180.0
#define YAW_MAX                    180.0

#define PITCH_MIN                 -30.0
#define PITCH_MAX                  30.0 


#define STEEP_ANGLE                35
#define TIME_TO_WAIT_AFTER_SHUTTING_ALL_ENGINES    30000
#define AUTO_STABILIZING_OFFSET    5

#define PITCH_OFFSET  -1.12
#define ROLL_OFFSET   0.4
#define YAW_OFFSET    0

// math constants
#define INV_RAD 0.01745                   // PI / 180

// sensor scaling
#define DT 0.0100                         // [s] 
//#define GYR_K 0.06956                   // [(deg/s)/LSB]
#define GYR_K 0.05580357143               // [(deg/s)/LSB] --> gyro scale 2000 deg/s gives us 70e-3 dps/digit (according to l3g4200d datasheet).
                                                           //  converting dps/digit to deg/s is simply dividing by one. 1 / 0.07 = 14.286. 
                                                           //  our LSB = 256. this gives us: 14.286 / 256.

//#define ACC_K 0.01399                   // [deg/LSB] --> 180 / PI / 4096
#define ACC_K 0.2238116387                // [deg/LSB] --> 180 / PI / 256

//#define VEL_K 0.002395                  // [(ft/s^2)/LSB] --> not really ft/s^2 but more like a m/s^2
                                                            //  G = 9.8 m/s^2
                                                            //  LSB = 4096
                                                            //  G / LSB
                                                            

#define VEL_K 0.1255946522                // [(ft/s^2)/LSB] --> G = 9.8 m/s^2 = 32.152230971129 ft/s^2
                                                            //  LSB = 256
                                                            // 32.1522 / 256

//#define ACC_Z_ZERO 4096                 // [LSB]
#define ACC_Z_ZERO 256                    // [LSB]

// filter settings
#define AA 0.1     // angle complementary filter, tau = 0.99s
#define AV 0.01     // z-velocity filter, tau = 0.99s
#define TV 0.99     // z-velocity filter

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
  boolean standby;
  uint8_t throttle;
  int8_t pitch;
  int8_t roll;
  int8_t yaw;
}Control;

Control _control;
L3G _gyro;
ADXL345 _acc;

double _rollSetPoint;     // given input
double _rollInput;        // current value
double _rollOutput;       // calculated output

double _yawSetPoint;
double _yawInput;
double _yawOutput;

double _pitchSetPoint;
double _pitchInput;
double _pitchOutput;

double _angles[3];

PID _pitch(&_pitchInput, &_pitchOutput, &_pitchSetPoint, 1, 0, 0, DIRECT);
PID _roll(&_rollInput, &_rollOutput, &_rollSetPoint, 1, 0, 0, DIRECT);
PID _yaw(&_yawInput, &_yawOutput, &_yawSetPoint, 1, 0, 0, DIRECT);

unsigned long loop_start; 
unsigned long _motorsStandbyTimestamp;

union btf {
    byte b[4];
    float f;
};
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
  pinMode(LED_1_PIN, OUTPUT);
  
  _control.standby = DEFAULT_STANDBY;
  _control.throttle = THROTTLE_MIN;
  _control.pitch = 0;
  _control.roll = 0;
  _control.yaw = 0;
  setMotorsPower(THROTTLE_MIN);
  
  _motorsStandbyTimestamp = 0;
  
  _roll.SetOutputLimits(ROLL_MIN, ROLL_MAX);
  _roll.SetSampleTime(PID_SAMPLE_TIME_MILLIS);
  _roll.SetMode(AUTOMATIC);
  
  _yaw.SetOutputLimits(YAW_MIN, YAW_MAX);
  _yaw.SetSampleTime(PID_SAMPLE_TIME_MILLIS);
  _yaw.SetMode(AUTOMATIC);

  _pitch.SetOutputLimits(PITCH_MIN, PITCH_MAX);
  _pitch.SetSampleTime(PID_SAMPLE_TIME_MILLIS);  
  _pitch.SetMode(AUTOMATIC);
  
  // init I2C
  Wire.begin();  
  initSensors();
}

void initSensors() { 
  _acc.begin();
  
  _gyro.init();
  _gyro.enableDefault();
  _gyro.scale(2000);
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
        
     case COMMAND_SETTINGS:
       _log->d("Command Settings");
       onCommandSettings(action, dataLength, data);
       break;
       
     case COMMAND_GET:
       _log->d("Command Get");
       onCommandGet(action, dataLength, data);
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
    case ACTION_STICKS: {
      _log->d("Action Sticks");
      if (millis() < _motorsStandbyTimestamp || _control.standby) {
        _log->d("onMessageReceived:: Motors are on standby");  
        return;
      }
      
      int8_t rawThrottle = (int8_t) data[0];
      int8_t rawPitch = (int8_t) data[1];
      int8_t rawRoll = (int8_t) data[2];
      int8_t rawYaw = (int8_t) data[3];
      
      if (rawThrottle < 0) {
        _log->d("Negative throttle detected, rounding to zero");
        rawThrottle = 0;
      }  

//      _log->d("Raw Throttle: ", rawThrottle);
//      _log->d("Raw Pitch: ", rawPitch);
//      _log->d("Raw Roll: ", rawRoll);
//      _log->d("Raw Yaw: ", rawYaw);
      
      int targetThrottle = map(rawThrottle, 0, INPUT_MAX, THROTTLE_MIN, THROTTLE_MAX);
      int targetYaw = map(rawYaw, INPUT_MIN, INPUT_MAX, YAW_MIN, YAW_MAX); 
      int targetPitch = map(rawPitch, INPUT_MIN, INPUT_MAX, PITCH_MIN, PITCH_MAX);
      int targetRoll = map(rawRoll, INPUT_MIN, INPUT_MAX, ROLL_MIN, ROLL_MAX); 
      
//      _log->d("Throttle: ", targetThrottle);
//      _log->d("Pitch: ", targetPitch);
//      _log->d("Roll: ", targetRoll);      
//      _log->d("Yaw: ", targetYaw);
  
      _control.throttle = targetThrottle;  
      _control.pitch = targetPitch;
      _control.roll = targetRoll;
      _control.yaw = targetYaw;
      break;
    }
    
    case ACTION_STANDBY: {
      _control.standby = (boolean) data[0];
      _log->d("Action Standby: ", _control.standby);
      if (_control.standby) {
          digitalWrite(LED_1_PIN, HIGH);
          setMotorsPower(THROTTLE_MIN);
          resetControl();
      } else {
          digitalWrite(LED_1_PIN, LOW);
      }
      
      break; 
    }
  }
}


/**
 * Handle settings requests
 *
 * @param action The action sent by the Android device
 * @param dataLength The length of "data"
 * @param data Pointer to the extra data sent by the device
 */
void onCommandSettings(byte action, byte dataLength, byte* data) {
  switch(action) {
    case ACTION_TUNE: {
      _log->d("Action Tune");
      
      btf conv;
      conv.b[0] = (byte) data[1];
      conv.b[1] = (byte) data[2];
      conv.b[2] = (byte) data[3];
      conv.b[3] = (byte) data[4];
      double Kp = (double) conv.f;
      
      conv.b[0] = (byte) data[5];
      conv.b[1] = (byte) data[6];
      conv.b[2] = (byte) data[7];
      conv.b[3] = (byte) data[8];
      double Ki = (double) conv.f;
      
      conv.b[0] = (byte) data[9];
      conv.b[1] = (byte) data[10];
      conv.b[2] = (byte) data[11];
      conv.b[3] = (byte) data[12];
      double Kd = (double) conv.f;
      
      int8_t type = (int8_t) data[0];
      switch (type) {
        case TUNE_PITCH:
          _log->d("Type: pitch");    
          _pitch.SetTunings(Kp, Ki, Kd);  
          break;
          
        case TUNE_ROLL:
          _log->d("Type: roll");
          _roll.SetTunings(Kp, Ki, Kd);
          break;
          
        case TUNE_YAW:
          _log->d("Type: yaw");
          _yaw.SetTunings(Kp, Ki, Kd);
          break;
      }

      _log->d("Kp: ", Kp);
      _log->d("Ki: ", Ki);
      _log->d("Kd: ", Kd);
      break;
    }
  }
}


/**
 * Handle get requests
 *
 * @param action The action sent by the Android device
 * @param dataLength The length of "data"
 * @param data Pointer to the extra data sent by the device
 */
void onCommandGet(byte action, byte dataLength, byte* data) {
  switch(action) {
    case ACTION_TUNE: {
      _log->d("Action Tune");
      byte msg[38];
      btf conv;
      int i = 0;
      msg[i++] = COMMAND_RESPONSE;
      msg[i++] = ACTION_TUNE;
      
      // pitch
      conv.f = (float) _pitch.GetKp();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];
      
      conv.f = (float) _pitch.GetKi();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];

      conv.f = (float) _pitch.GetKd();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];
      
      // roll
      conv.f = (float) _roll.GetKp();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];
      
      conv.f = (float) _roll.GetKi();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];

      conv.f = (float) _roll.GetKd();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];

      // yaw
      conv.f = (float) _yaw.GetKp();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];
      
      conv.f = (float) _yaw.GetKi();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];

      conv.f = (float) _yaw.GetKd();            
      msg[i++] = conv.b[0];
      msg[i++] = conv.b[1];
      msg[i++] = conv.b[2];
      msg[i++] = conv.b[3];

      sendToDevice(msg, i);
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
  if (millis() < _motorsStandbyTimestamp || _control.standby) {
    _log->d("onLoop:: Motors are on standby");  
    digitalWrite(LED_1_PIN, HIGH);
    return;
  }
  
  // every 10 ms (should be the same as our sampling rate constant DT)
  if (micros() - loop_start >= 10000) {
    loop_start = micros();
    
    // calculate true angles from accelerometer, gyroscope and magnetometer
    double* angles = getAngles();
    _pitchInput = angles[0];  
    _rollInput = angles[1];
    _yawInput = angles[2];  
    
    if (abs(_pitchInput) >= STEEP_ANGLE || abs(_rollInput) >= STEEP_ANGLE) {
      // detect steep angle - TURN OFF ALL MOTORS IMMEDIATELY 
      _log->d("Steep angle detected! turning off all motors immediately");
      digitalWrite(LED_1_PIN, HIGH);
      setMotorsPower(THROTTLE_MIN);
      resetControl();
          
      _motorsStandbyTimestamp = millis() + TIME_TO_WAIT_AFTER_SHUTTING_ALL_ENGINES;
      return;
    } 
    else {
      digitalWrite(LED_1_PIN, LOW);
    } 
    
    
    // set target values
    _pitchSetPoint = _control.pitch;   
    _rollSetPoint = _control.roll;
    _yawSetPoint = _control.yaw; 
   
  //  _log->d("pitch input: ", _pitchInput);
  //  _log->d("roll input: ", _rollInput);
  //  _log->d("yaw input: ", _yawInput);
  // 
  //  _log->d("pitch set point: ", _pitchSetPoint);
  //  _log->d("roll set point: ", _rollSetPoint);
  //  _log->d("yaw set point: ", _yawSetPoint);
    
    // let the PID calculate the output from defined variables: xxxInput & xxxSetPoint.
    // output will be saved at xxxOutput
    _roll.Compute();
    _yaw.Compute();
    _pitch.Compute();
  
    _log->d("pitch output: ", _pitchOutput);
    _log->d("roll output: ", _rollOutput);
    _log->d("yaw output: ", _yawOutput);
  
  
    // now that we have all the values, apply these values to all our motors
    float pitch_2 = _pitchOutput / 2;
    float roll_2 = _rollOutput / 2;
  
    int motor1Power = _control.throttle + pitch_2 - roll_2 + _yawOutput;
    int motor2Power = _control.throttle + pitch_2 + roll_2 - _yawOutput;
    int motor3Power = _control.throttle - pitch_2 + roll_2 + _yawOutput;
    int motor4Power = _control.throttle - pitch_2 - roll_2 - _yawOutput; 
    
    _motor1.write(motor1Power);
    _motor2.write(motor2Power);
    _motor3.write(motor3Power);
    _motor4.write(motor4Power);
    
  //  _log->d("Motor 1 Power: ", motor1Power);
  //  _log->d("Motor 2 Power: ", motor2Power);
  //  _log->d("Motor 3 Power: ", motor3Power);
  //  _log->d("Motor 4 Power: ", motor4Power);
  }
}










////
/////// Read any futher only if curious...
////



/**
 * Read angles from all the following sensors: accelerometer, gyroscope and magnetometer
 *
 * @return 0:Pitch, 1:Roll, 2:Yaw
 */
double* getAngles() {
  // read sensors
  // ------------
  signed int raw_x, raw_y, raw_z;
  double gyro_pitch, gyro_roll, gyro_yaw;
  double accel_pitch, accel_roll;
  
  // read from the L3G4200D gyro
  _gyro.read();
  
  // convert raw gyro data to [deg/s]
  gyro_pitch = -_gyro.g.x * GYR_K;
  gyro_roll = _gyro.g.y * GYR_K;
  gyro_yaw = _gyro.g.z * GYR_K;
  
  // read from the ADXL345 accelerometer
  _acc.read(&raw_x, &raw_y, &raw_z);
  
  // convert raw accel data to [deg] for pitch/roll
  accel_roll = -(double) raw_x * ACC_K;
  accel_pitch = -(double) raw_y * ACC_K;

  // convert raw accel data to [ft/s^2] for z
  //accel_z = (float)(raw_z - ACC_Z_ZERO) * VEL_K;
  
  
  // filter angles
  // -------------
  // complementary filters on pitch and roll angles
  float int_pitch, int_roll;
  float error_pitch, error_roll;
  
  // integrate pitch rate into pitch angle
  int_pitch = _angles[0] + gyro_pitch * DT;
  // integrate component of yaw rate into pitch angle
  int_pitch += _angles[1] * INV_RAD * gyro_yaw * DT;
  // filter with error feedback from pitch accelerometer
  error_pitch = accel_pitch - int_pitch + PITCH_OFFSET;
  _angles[0] = int_pitch + AA * error_pitch;
  
  // integrate roll rate into roll angle
  int_roll = _angles[1] + gyro_roll * DT;
  // integrate component of yaw rate into roll angle
  int_roll -= _angles[0] * INV_RAD * gyro_yaw * DT;
  // filter with error feedback from roll accelerometer
  error_roll = accel_roll - int_roll + ROLL_OFFSET;
  _angles[1] = int_roll + AA * error_roll;
  

  // set global values and offsets
  //_angles[0] += PITCH_OFFSET;
  //_angles[1] += ROLL_OFFSET;
  _angles[2] = gyro_yaw;
  
//  if (DEBUG) {
//    Serial.print(_angles[0]);
//    Serial.print(" | ");
//    Serial.print(_angles[1]);
//    Serial.print(" | ");
//    Serial.println(_angles[2]);  
//  }
  
  return _angles;
}
    
/**
 * Set all motors to provided power
 *
 * @param power
 */
void setMotorsPower(int power) {
  _motor1.write(power);
  _motor2.write(power);
  _motor3.write(power);
  _motor4.write(power);
}    

/**
 * Reset motor's control
 */
void resetControl() {
  _control.throttle = THROTTLE_MIN;
  _control.pitch = 0;
  _control.roll = 0;
  _control.yaw = 0;
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
const char *USB_SERIAL       = "0000000000000007";

                    
// command (1 byte), action (1 byte), data-length (1 byte), data (X bytes) 
AndroidAccessory *_accessory;

// members-states
long _lastTimeReconnectedToUsb;



/**
 * Called once when the arduino first loads (or resets)
 */
void setup(){
  Serial.begin(9600);
  _accessory = new AndroidAccessory(USB_MANUFACTURER,
                              USB_MODEL,
                              USB_DESCRIPTION,
                              USB_VERSION,
                              USB_SITE,
                              USB_SERIAL);
  _accessory->powerOn();
  _lastTimeReconnectedToUsb = 0;
  
  onCreate();
}

/**
 * loop forever. 
 * Arduino calls this method in an infinite loop after returning from method "setup"
 */
void loop() {   
  if (_accessory->isConnected()) {  
    
    byte msg[BUFFER_SIZE];
    int len = _accessory->read(msg, BUFFER_SIZE, 1);
    if (len > 0) {
      Serial.print("read: ");
      Serial.print(len, DEC);
      Serial.println(" bytes");

      handleMsgFromDevice(msg);      
      if (DEBUG) {
        sendAck();
      }
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
  delete _accessory;
  _accessory = new AndroidAccessory(USB_MANUFACTURER,
                              USB_MODEL,
                              USB_DESCRIPTION,
                              USB_VERSION,
                              USB_SITE,
                              USB_SERIAL);
  _accessory->powerOn();
}    

/**
 * Send acknowledge to connected Android device
 */ 
void sendAck() {
  if (_accessory->isConnected()) {
    byte msg[1];
    msg[0] = COMMAND_ACK;
    _accessory->write(msg, 1);
  }  
}

/**
 * Send data to connected Android device
 *
 * @param msg Pointer to the data
 * @param msgLength
 */
void sendToDevice(byte* msg, int msgLength) {
  if (_accessory->isConnected()) {
    _accessory->write(msg, msgLength);
  } else {
    _log->d("Can't send to device since it is not connected");
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
