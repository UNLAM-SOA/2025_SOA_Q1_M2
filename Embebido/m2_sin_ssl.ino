#include <WiFiClientSecure.h>
#include <WiFi.h>
#include "PubSubClient.h"

#define LIGHT_SENSOR_PIN 32
#define MOTOR_FORWARD_PIN 26
#define MOTOR_BACKWARD_PIN 25
#define MOTOR_SPEED_PIN 27
#define LED_PIN 18
#define FC_START_PIN 15
#define FC_END_PIN 23
#define LIGHT_THRESHOLD 1000
#define SLEEP_INTERVAL_MS 1000
#define PRIORITY_LS 1
#define PRIORITY_LIGHT_SENSOR 2
#define PRIORITY 1
#define TIME_OUT 2000
#define STACK_SIZE 8192
#define MAX_EVENTS_QUEUE 6
#define SERIAL_MONITOR 115200
#define SPEED 150

// CLIENTE WIFI
WiFiClientSecure espClient;
PubSubClient client(espClient);

// CONFIG RED
const char *ssid = "SO Avanzados";
const char *password = "SOA.2019";

// CONFIG MQTT
const char *mqtt_server = "192.168.30.116";
const int port = 1883;

const char *user_name = "m2";
const char *user_pass = "SOA.2019";
const char *clientId = "m2wokwi";

// TOPICOS MQTT
const char *topic_persiana = "/persiana";
const char *topicAppPersiana = "/app_persiana";
char topic_persiana_subscribe[200];

enum Mode
{
  AUTO,
  MANUAL,
  MODE_QUANTITY
};

enum Cmd
{
  OPEN,
  CLOSE,
  STOP,
  MODE_MANUAL,
  MODE_AUTO,
  INVALID_CMD,
  CMD_QUANTITY
};

enum State
{
  STOPPED,
  FORWARDING,
  BACKWARDING,
  MFORWARDING,
  MBACKWARDING,
  STATE_QUANTITY
};

enum Event
{
  EVT_GO_UP,
  EVT_GO_DOWN,
  EVT_PAUSE,
  EVT_FC_END,
  EVT_FC_START,
  EVT_MGO_UP,
  EVT_MGO_DOWN,
  EVT_CHANGE_MODE_MANUAL,
  EVT_CHANGE_MODE_AUTO,
  EVENT_QUANTITY
};

// FUNCIONES PARA ACTUALIZAR Y LEER LOS PINES
void dcMotorStop();
void dcMotorSpeed();
void dcMotorBackward();
void dcMotorForward();
boolean isLigthOn();

// FUNCIONES PARA LOS EVENTOS
void goUp();
void goDown();
void mGoUp();
void mGoDown();
void pauseMotor();
void noChange();
void changeModeManual();
void changeModeAuto();

// MAQUINA DE ESTADOS
void stateMachine();
void getEvent();

// FUNCIONES PARA MANEJAR LOS COMANDOS
Cmd cmdMapper(String cmd);
void cmdAuto();
void cmdManual();
void cmdPause();
void cmdGoDown();
void cmdGoUp();
void cmdInvalid();

// TAREAS QUE LEEN SENSORES
void lightSensorTask(void *p);
void fcTask(void *p);
void cmdTask(void *p);

// FUNCIONES MQTT
void wifiConnect();
void mqttTask(void *p);

typedef void (*Function)();

Function transitionMatrix[STATE_QUANTITY][EVENT_QUANTITY] = {
    //                  EVT_GO_UP EVT_GO_DOWN   EVT_PAUSE           EVT_FC_END    EVT_FC_START EVT_MGO_UP  EVT_MGO_DOWN  EVT_CHANGE_MODE_MANUAL EVT_CHANGE_MODE_AUTO
    /* STOPPED */ {goUp, goDown, noChange, noChange, noChange, mGoUp, mGoDown, changeModeManual, changeModeAuto},
    /* FORWARDING */ {noChange, goDown, changeModeManual, pauseMotor, noChange, noChange, noChange, changeModeManual, noChange},
    /* BACKWARDING */ {goUp, noChange, changeModeManual, noChange, pauseMotor, noChange, noChange, changeModeManual, noChange},
    /* MFORWARDING */ {noChange, noChange, pauseMotor, pauseMotor, noChange, noChange, mGoDown, noChange, changeModeAuto},
    /* MBACKWARDING */ {noChange, noChange, pauseMotor, noChange, pauseMotor, mGoUp, noChange, noChange, changeModeAuto}};

//                                   OPEN     CLOSE      STOP      MODE_MANUAL MODE_AUTO INVALID_CMD
Function cmdActions[CMD_QUANTITY] = {cmdGoUp, cmdGoDown, cmdPause, cmdManual, cmdAuto, cmdInvalid};

String stateStrings[STATE_QUANTITY] = {"DETENIDA", "ABIERTA", "CERRADA", "ABIERTA", "CERRADA"};
String modeStrings[MODE_QUANTITY] = {"AUTO", "MANUAL"};

State currentState = STOPPED;
Event currentEvent = EVT_PAUSE;
Mode currentConfig = MANUAL;
QueueHandle_t eventQueue;

void setup()
{
  Serial.begin(SERIAL_MONITOR);
  Serial.println("SISOP AV UNLAM M2 Q1 2025 - Cortina Roller 1");

  pinMode(MOTOR_FORWARD_PIN, OUTPUT);
  pinMode(MOTOR_BACKWARD_PIN, OUTPUT);
  pinMode(MOTOR_SPEED_PIN, OUTPUT);
  pinMode(FC_START_PIN, INPUT);
  pinMode(FC_END_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT);

  dcMotorStop();  // iniciliza motor apagado
  dcMotorSpeed(); // setea speed

  eventQueue = xQueueCreate(MAX_EVENTS_QUEUE, sizeof(Event));
  xTaskCreate(lightSensorTask, "Sensor de Luz", STACK_SIZE, NULL, PRIORITY_LIGHT_SENSOR, NULL);
  xTaskCreate(fcTask, "Sensor Final de Carrera", STACK_SIZE, NULL, PRIORITY_LS, NULL);
  xTaskCreate(cmdTask, "Cmd", STACK_SIZE, NULL, PRIORITY, NULL);
  xTaskCreate(mqttTask, "MQTT", STACK_SIZE, NULL, PRIORITY, NULL);
  xTaskCreate(sendStateToMQTTTask, "Send State to MQTT", STACK_SIZE, NULL, PRIORITY, NULL);

  // MQTT y WIFI
  wifiConnect();
  espClient.setInsecure();

  client.setServer(mqtt_server, port);
  client.setCallback(callback);
}

void loop()
{
  stateMachine();
}

// MAQUINA DE ESTADOS
void stateMachine()
{
  getEvent();
  transitionMatrix[currentState][currentEvent]();
}

void getEvent()
{
  Event newEvent;
  if ((xQueueReceive(eventQueue, &newEvent, portMAX_DELAY)) == pdPASS)
  {
    if (currentEvent == EVT_FC_END && newEvent == EVT_GO_UP)
      return;
    if (currentEvent == EVT_FC_START && newEvent == EVT_GO_DOWN)
      return;
    currentEvent = newEvent;
  }
}

// FUNCIONES PARA ACTUALIZAR Y LEER LOS PINES
boolean isLigthOn()
{
  int lightValue = analogRead(LIGHT_SENSOR_PIN);
  return lightValue >= LIGHT_THRESHOLD;
}

void dcMotorForward()
{
  digitalWrite(MOTOR_FORWARD_PIN, HIGH);
  digitalWrite(MOTOR_BACKWARD_PIN, LOW);
  digitalWrite(LED_PIN, HIGH);
}

void dcMotorBackward()
{
  digitalWrite(MOTOR_FORWARD_PIN, LOW);
  digitalWrite(MOTOR_BACKWARD_PIN, HIGH);
  digitalWrite(LED_PIN, HIGH);
}

void dcMotorSpeed()
{
  analogWrite(MOTOR_SPEED_PIN, SPEED);
}

void dcMotorStop()
{
  digitalWrite(MOTOR_FORWARD_PIN, LOW);
  digitalWrite(MOTOR_BACKWARD_PIN, LOW);
  digitalWrite(LED_PIN, LOW);
}

// FUNCIONES PARA LOS EVENTOS
void goUp()
{
  currentState = FORWARDING;
  dcMotorForward();
}

void goDown()
{
  currentState = BACKWARDING;
  dcMotorBackward();
}

void mGoUp()
{
  currentState = MFORWARDING;
  dcMotorForward();
}

void mGoDown()
{
  currentState = MBACKWARDING;
  dcMotorBackward();
}

void changeModeManual()
{
  currentConfig = MANUAL;
  pauseMotor();
}

void changeModeAuto()
{
  currentConfig = AUTO;
  pauseMotor();
}

void pauseMotor()
{
  currentState = STOPPED;
  dcMotorStop();
}

void noChange()
{
  // Do nothing
}

// FUNCIONES PARA MANEJAR LOS COMANDOS
Cmd cmdMapper(String cmd)
{
  cmd.toLowerCase();
  if (cmd == "abrir")
    return OPEN;
  if (cmd == "cerrar")
    return CLOSE;
  if (cmd == "pausar")
    return STOP;
  if (cmd == "cm manual")
    return MODE_MANUAL;
  if (cmd == "cm auto")
    return MODE_AUTO;
  return INVALID_CMD;
}

void cmdGoUp()
{
  if (currentConfig == MANUAL)
  {
    Event evt;
    if (digitalRead(FC_END_PIN) == LOW)
    {
      evt = EVT_MGO_UP;
      xQueueSend(eventQueue, &evt, TIME_OUT);
      Serial.println("Abriendo cortina");
    }
    else
    {
      Serial.println("La cortina ya esta completamente abierta");
    }
  }
}

void cmdGoDown()
{
  if (currentConfig == MANUAL)
  {
    Event evt;
    if (digitalRead(FC_START_PIN) == LOW)
    {
      evt = EVT_MGO_DOWN;
      xQueueSend(eventQueue, &evt, TIME_OUT);
      Serial.println("Cerrando cortina");
    }
    else
    {
      Serial.println("La cortina ya esta completamente cerrada");
    }
  }
}

void cmdPause()
{
  Event evt = EVT_PAUSE;
  xQueueSend(eventQueue, &evt, TIME_OUT);
  Serial.println("La cortina se a detenido");
}

void cmdManual()
{
  if (currentConfig == AUTO)
  {
    Event evt = EVT_CHANGE_MODE_MANUAL;
    xQueueSend(eventQueue, &evt, TIME_OUT);
    Serial.println("Se cambio a modo MANUAL");
  }
  else
  {
    Serial.println("Ya se encuentra en modo manual");
  }
}

void cmdAuto()
{
  if (currentConfig == MANUAL)
  {
    Event evt = EVT_CHANGE_MODE_AUTO;
    xQueueSend(eventQueue, &evt, TIME_OUT);
    Serial.println("Se cambio a modo AUTO");
  }
  else
  {
    Serial.println("Ya se encuentra en modo AUTO");
  }
}

void cmdInvalid()
{
  Serial.println("Comando no reconocido");
}

// TAREAS QUE LEEN SENSORES
void lightSensorTask(void *p)
{
  const TickType_t delayTimeOut = 5000;
  Event evt;

  while (true)
  {
    vTaskDelay(delayTimeOut);
    if (currentConfig != AUTO)
      continue;

    bool lightOn = isLigthOn();
    int pinState = digitalRead(lightOn ? FC_END_PIN : FC_START_PIN);

    if (pinState == HIGH)
      continue;

    evt = lightOn ? EVT_GO_UP : EVT_GO_DOWN;
    xQueueSend(eventQueue, &evt, TIME_OUT);
  }
}

void fcTask(void *p)
{
  Event evt;
  TickType_t delayTimeOut = 200;
  while (true)
  {
    if (digitalRead(FC_END_PIN) == HIGH)
    {
      evt = EVT_FC_END;
      xQueueSend(eventQueue, &evt, TIME_OUT);
    }
    if (digitalRead(FC_START_PIN) == HIGH)
    {
      evt = EVT_FC_START;
      xQueueSend(eventQueue, &evt, TIME_OUT);
    }
    vTaskDelay(delayTimeOut);
  }
}

void sendStateToMQTTTask()
{
  TickType_t delayTimeOut = 1000;
  while (true)
  {
    if (client.connected())
    {
      client.publish(topicAppPersiana, generatePayload());
    }
    vTaskDelay(delayTimeOut);
  }
}

void cmdTask(void *p)
{
  TickType_t delayTimeOut = 500;
  while (true)
  {
    if (Serial.available())
    {
      String console_str = Serial.readStringUntil('\n');
      console_str.trim();
      Cmd cmd = cmdMapper(console_str);
      cmdActions[cmd]();
    }
    vTaskDelay(delayTimeOut);
  }
}

void mqttTask(void *p)
{
  while (true)
  {
    if (!client.connected())
    {
      mqttReconnect();
    }
    client.loop();
    vTaskDelay(10 / portTICK_PERIOD_MS); // Ejecuta cada 10 ms
  }
}

// FUNCIONES MQTT
void wifiConnect()
{
  Serial.println("+ Conectando a WiFi");
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED)
  {
    vTaskDelay(500 / portTICK_PERIOD_MS);
    Serial.print(".");
  }
  Serial.println(" ==> Conectado.");
}

void mqttReconnect()
{
  while (!client.connected())
  {
    Serial.println("+ Intentando conexión MQTT...");

    if (client.connect(clientId, user_name, user_pass))
    {
      Serial.printf("==> '%s' conectado\n", clientId);
      client.subscribe(topic_persiana);
    }
    else
    {
      Serial.printf("Error, rc= %d. Intentando en 5 segundos\n", client.state());
      vTaskDelay(5000 / portTICK_PERIOD_MS);
    }
  }
}

void callback(char *topic, byte *message, unsigned int length)
{
  String msg;
  for (int i = 0; i < length; i++)
  {
    msg += (char)message[i];
  }

  Serial.printf("+ Mensaje recibido: %s\n", msg);
  // Ejecuto comando
  Cmd cmd = cmdMapper(String(msg));
  cmdActions[cmd]();
  // Publico el estado actual de la persiana
  client.publish(topicAppPersiana, generatePayload());
}

char *generatePayload()
{
  int light = analogRead(LIGHT_SENSOR_PIN);
  String payload = "{";
  payload += "\"estado\": \"" + stateStrings[currentState] + "\", ";
  payload += "\"modo\": \"" + modeStrings[currentConfig] + "\", ";
  payload += "\"luz\": " + light;
  payload += "}";
  return payload.c_str();
}