# Sharing readings with Home Assistant over MQTT

Optional: the Climate view can publish its readings to Home Assistant. This is
an example of pushing data to another system and can be left unused or removed;
the rest of the application does not depend on it.

The Climate view's **Share with Home Assistant** card is the example of pushing
data to another system. `bme280/ClimatePublisher` publishes the readings over
MQTT using Home Assistant's [MQTT discovery](https://www.home-assistant.io/integrations/mqtt/#mqtt-discovery):
one retained config message per sensor, a shared `device` block that groups them
into one device, a JSON state topic every entity reads with its own
`value_template`, and an availability topic whose `offline` value is also the
client's last will. Home Assistant creates the device with temperature, humidity
and pressure by itself; there is nothing to configure on that side beyond a
broker.

The card tries to be automatic:

1. It looks for Home Assistant with mDNS (`_home-assistant._tcp`), which the
   `HomeAssistantFinder` in pi-helpers does with JmDNS, and checks whether a
   broker answers on port 1883 on the same host, where the Mosquitto add-on
   listens on Home Assistant OS.
2. **Connect** connects to that broker. If the broker accepts anonymous
   clients this is the whole setup. The Mosquitto add-on does not, so a small
   dialog asks once for a login. Either a Home Assistant user works (the add-on
   checks Home Assistant's users; make a separate one under Settings → People →
   Users, without administrator rights) or a broker-only login defined in the
   add-on's configuration under `logins`. The Docker test setup below needs no
   login at all.
3. Once connected, discovery and the first state go out immediately, then a
   state every 30 seconds (adjustable). Home Assistant shows the device under
   Settings → Devices & services → MQTT within seconds.

For a finished application, put the broker in `application.properties` instead
of the UI: setting `starter.mqtt.host` (with the optional `starter.mqtt.port`,
`username`, `password`, `device-id`, `topic-prefix`, `interval` and `enabled`)
makes those settings win, publishing starts at boot, and the card only reports
what is happening. `MqttConfig` in pi-helpers reads them.

**Stop sharing** publishes `offline` and disconnects; the entities stay in Home
Assistant as unavailable. **Remove from Home Assistant** clears the retained
discovery messages so the device disappears. **Use another broker** exposes all
fields for a broker mDNS did not find. Settings, including the password in plain
text, are kept in `data/homeassistant.properties` (`starter.data-dir`) and the
connection resumes on the next start.

Topics, with the default prefix and a device id derived from the Pi's serial
number (or the host name):

```
pi-starter/pi-1234abcd/climate/state    {"temperature":22.4,"humidity":45.1,"pressure":1012.3,"at":"…"}
pi-starter/pi-1234abcd/status           online | offline
homeassistant/sensor/pi-starter-pi-1234abcd/temperature/config   (retained discovery)
```

The reusable parts live in pi-helpers: `MqttPublisher` (HiveMQ MQTT client with
automatic reconnect), `HomeAssistantDiscovery` (pure payload builders, unit
tested in `HomeAssistantDiscoveryTest`), `HomeAssistantFinder`, `MqttSettings`
and `SettingsStore`. Copy `ClimatePublisher` for your own sensor and change the
sensors and the state JSON.

## A Home Assistant to test against, in Docker

Without a Home Assistant at hand, run one next to a Mosquitto broker. On a
**Linux host** (a second Pi, a NAS, a Linux laptop) use host networking, which
is also what Home Assistant's own instructions require; then mDNS works and the
card finds the instance by itself:

```yaml
# docker-compose.yml
services:
  homeassistant:
    image: ghcr.io/home-assistant/home-assistant:stable
    network_mode: host
    volumes:
      - ./ha-config:/config
    restart: unless-stopped
  mosquitto:
    image: eclipse-mosquitto:2
    network_mode: host
    # The image ships a config that listens on 1883 and allows anonymous clients.
    # Fine on a test network, and it makes the card's Connect fully automatic.
    command: mosquitto -c /mosquitto-no-auth.conf
    restart: unless-stopped
```

```sh
docker compose up -d
```

To try the login dialog instead, run a broker that insists on a password:

```sh
docker run -d --name mosquitto-auth -p 1884:1884 eclipse-mosquitto:2 sh -c \
  'mosquitto_passwd -b -c /mosquitto/pw pi secret && chown mosquitto /mosquitto/pw && chmod 600 /mosquitto/pw \
   && printf "listener 1884\npassword_file /mosquitto/pw\nallow_anonymous false\n" > /mosquitto/c.conf \
   && exec mosquitto -c /mosquitto/c.conf'
```

Without host networking (containers on Docker's default bridge, or with the
broker in a separate container) the card still finds Home Assistant by mDNS but
reports that no broker answers on its address, because the broker lives at a
different IP; use *Use another broker* with the Mosquitto container's address.
This is the setup this example was verified against: Home Assistant 2026.9
created the device with three sensors from the discovery messages, marked them
unavailable on *Stop sharing* and dropped them on *Remove*; the login dialog was
exercised against a second Mosquitto with `allow_anonymous false`.

Open `http://<host>:8123`, create the owner account, then Settings → Devices &
services → Add integration → **MQTT** with broker `<host>` and port 1883. This
container has no add-ons, so the MQTT integration is added by hand once;
discovery is on by default. Start the Pi Starter application, open Climate and
press Connect.

On **Docker Desktop** (macOS, Windows) containers run in a VM and mDNS does not
reach your LAN, so the card will not find the instance. Publish the ports
instead and fill in the broker manually under *Use another broker*:

```sh
docker run -d --name homeassistant -p 8123:8123 -v "$PWD/ha-config:/config" ghcr.io/home-assistant/home-assistant:stable
docker run -d --name mosquitto -p 1883:1883 eclipse-mosquitto:2 mosquitto -c /mosquitto-no-auth.conf
```

With the Pi Starter on another machine, the broker host is your computer's LAN
address; the MQTT integration in Home Assistant then uses `host.docker.internal`
or that same address.

To watch the messages:

```sh
mosquitto_sub -h <host> -v -t 'pi-starter/#' -t 'homeassistant/#'
```
