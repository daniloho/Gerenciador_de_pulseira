/*  Copyright (C) 2017-2019 Andreas Shimokawa, Carsten Pfeiffer, Matthieu
    Baerts, Roi Greenberg

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.Pulseira.service.devices.huami.amazfitbip;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import nodomain.freeyourgadget.Pulseira.devices.huami.HuamiCoordinator;
import nodomain.freeyourgadget.Pulseira.devices.huami.HuamiFWHelper;
import nodomain.freeyourgadget.Pulseira.devices.huami.HuamiService;
import nodomain.freeyourgadget.Pulseira.devices.huami.amazfitbip.AmazfitBipFWHelper;
import nodomain.freeyourgadget.Pulseira.devices.huami.amazfitbip.AmazfitBipService;
import nodomain.freeyourgadget.Pulseira.model.CallSpec;
import nodomain.freeyourgadget.Pulseira.model.DeviceType;
import nodomain.freeyourgadget.Pulseira.model.NotificationSpec;
import nodomain.freeyourgadget.Pulseira.model.NotificationType;
import nodomain.freeyourgadget.Pulseira.model.RecordedDataTypes;
import nodomain.freeyourgadget.Pulseira.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.Pulseira.service.btle.profiles.alertnotification.AlertCategory;
import nodomain.freeyourgadget.Pulseira.service.btle.profiles.alertnotification.AlertNotificationProfile;
import nodomain.freeyourgadget.Pulseira.service.btle.profiles.alertnotification.NewAlert;
import nodomain.freeyourgadget.Pulseira.service.devices.huami.HuamiIcon;
import nodomain.freeyourgadget.Pulseira.service.devices.huami.HuamiSupport;
import nodomain.freeyourgadget.Pulseira.service.devices.huami.operations.FetchActivityOperation;
import nodomain.freeyourgadget.Pulseira.service.devices.huami.operations.FetchSportsSummaryOperation;
import nodomain.freeyourgadget.Pulseira.service.devices.huami.operations.HuamiFetchDebugLogsOperation;
import nodomain.freeyourgadget.Pulseira.service.devices.miband.NotificationStrategy;
import nodomain.freeyourgadget.Pulseira.util.StringUtils;

public class AmazfitBipSupport extends HuamiSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AmazfitBipSupport.class);

    public AmazfitBipSupport() {
        super(LOG);
    }

    @Override
    public NotificationStrategy getNotificationStrategy() {
        return new AmazfitBipTextNotificationStrategy(this);
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        if (notificationSpec.type == NotificationType.GENERIC_ALARM_CLOCK) {
            onAlarmClock(notificationSpec);
            return;
        }

        String senderOrTitle = StringUtils.getFirstOf(notificationSpec.sender, notificationSpec.title);

        String message = StringUtils.truncate(senderOrTitle, 32) + "\0";
        if (notificationSpec.subject != null) {
            message += StringUtils.truncate(notificationSpec.subject, 128) + "\n\n";
        }
        if (notificationSpec.body != null) {
            message += StringUtils.truncate(notificationSpec.body, 128);
        }

        try {
            TransactionBuilder builder = performInitialized("new notification");

            byte customIconId = HuamiIcon.mapToIconId(notificationSpec.type);
            AlertCategory alertCategory = AlertCategory.CustomHuami;

            // The SMS icon for AlertCategory.SMS is unique and not available as iconId
            if (notificationSpec.type == NotificationType.GENERIC_SMS) {
                alertCategory = AlertCategory.SMS;
            }
            // EMAIL icon does not work in FW 0.0.8.74, it did in 0.0.7.90
            else if (customIconId == HuamiIcon.EMAIL) {
                alertCategory = AlertCategory.Email;
            }

            int maxLength = 230;
            if (characteristicChunked != null) {
                int prefixlength = 2;

                // We also need a (fake) source name for Mi Band 3 for SMS/EMAIL, else the message is not displayed
                byte[] appSuffix = "\0 \0".getBytes();
                int suffixlength = appSuffix.length;

                if (alertCategory == AlertCategory.CustomHuami) {
                    String appName;
                    prefixlength = 3;
                    final PackageManager pm = getContext().getPackageManager();
                    ApplicationInfo ai = null;
                    try {
                        ai = pm.getApplicationInfo(notificationSpec.sourceAppId, 0);
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }

                    if (ai != null) {
                        appName = "\0" + pm.getApplicationLabel(ai) + "\0";
                    } else {
                        appName = "\0" + "UNKNOWN" + "\0";
                    }
                    appSuffix = appName.getBytes();
                    suffixlength = appSuffix.length;
                }
                if (gbDevice.getType() == DeviceType.MIBAND4 || gbDevice.getType() == DeviceType.AMAZFITGTR) {
                    prefixlength += 4;
                }

                byte[] rawmessage = message.getBytes();
                int length = Math.min(rawmessage.length, maxLength - prefixlength);

                byte[] command = new byte[length + prefixlength + suffixlength];
                int pos = 0;
                command[pos++] = (byte) alertCategory.getId();
                if (gbDevice.getType() == DeviceType.MIBAND4 || gbDevice.getType() == DeviceType.AMAZFITGTR) {
                    command[pos++] = 0; // TODO
                    command[pos++] = 0;
                    command[pos++] = 0;
                    command[pos++] = 0;
                }
                command[pos++] = 1;
                if (alertCategory == AlertCategory.CustomHuami) {
                    command[pos] = customIconId;
                }

                System.arraycopy(rawmessage, 0, command, prefixlength, length);
                System.arraycopy(appSuffix, 0, command, prefixlength + length, appSuffix.length);

                writeToChunked(builder, 0, command);
            } else {
                AlertNotificationProfile<?> profile = new AlertNotificationProfile(this);
                NewAlert alert = new NewAlert(alertCategory, 1, message, customIconId);
                profile.setMaxLength(maxLength);
                profile.newAlert(builder, alert);
            }
            builder.queue(getQueue());
        } catch (IOException ex) {
            LOG.error("Unable to send notification to Amazfit Bip", ex);
        }
    }

    @Override
    public void onFindDevice(boolean start) {
        CallSpec callSpec = new CallSpec();
        callSpec.command = start ? CallSpec.CALL_INCOMING : CallSpec.CALL_END;
        callSpec.name = "Gadgetbridge";
        onSetCallState(callSpec);
    }

    @Override
    public void handleButtonEvent() {
        // ignore
    }

    @Override
    protected AmazfitBipSupport setDisplayItems(TransactionBuilder builder) {
        if (gbDevice.getFirmwareVersion() == null) {
            LOG.warn("Device not initialized yet, won't set menu items");
            return this;
        }

        Set<String> pages = HuamiCoordinator.getDisplayItems(gbDevice.getAddress());
        LOG.info("Setting display items to " + (pages == null ? "none" : pages));
        byte[] command = AmazfitBipService.COMMAND_CHANGE_SCREENS.clone();

        boolean shortcut_weather = false;
        boolean shortcut_alipay = false;

        if (pages != null) {
            if (pages.contains("status")) {
                command[1] |= 0x02;
            }
            if (pages.contains("activity")) {
                command[1] |= 0x04;
            }
            if (pages.contains("weather")) {
                command[1] |= 0x08;
            }
            if (pages.contains("alarm")) {
                command[1] |= 0x10;
            }
            if (pages.contains("timer")) {
                command[1] |= 0x20;
            }
            if (pages.contains("compass")) {
                command[1] |= 0x40;
            }
            if (pages.contains("settings")) {
                command[1] |= 0x80;
            }
            if (pages.contains("alipay")) {
                command[2] |= 0x01;
            }
            if (pages.contains("shortcut_weather")) {
                shortcut_weather = true;
            }
            if (pages.contains("shortcut_alipay")) {
                shortcut_alipay = true;
            }
        }
        builder.write(getCharacteristic(HuamiService.UUID_CHARACTERISTIC_3_CONFIGURATION), command);
        setShortcuts(builder, shortcut_weather, shortcut_alipay);

        return this;
    }

    private void setShortcuts(TransactionBuilder builder, boolean weather, boolean alipay) {
        LOG.info("Setting shortcuts: weather=" + weather + " alipay=" + alipay);

        // Basically a hack to put weather first always, if alipay is the only enabled one
        // there are actually two alipays set but the second one disabled.... :P
        byte[] command = new byte[]{0x10,
                (byte) ((alipay || weather) ? 0x80 : 0x00), (byte) (weather ? 0x02 : 0x01),
                (byte) ((alipay && weather) ? 0x81 : 0x01), 0x01,
        };

        builder.write(getCharacteristic(HuamiService.UUID_CHARACTERISTIC_3_CONFIGURATION), command);
    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
        try {
            // FIXME: currently only one data type supported, these are meant to be flags
            if (dataTypes == RecordedDataTypes.TYPE_ACTIVITY) {
                new FetchActivityOperation(this).perform();
            } else if (dataTypes == RecordedDataTypes.TYPE_GPS_TRACKS) {
                new FetchSportsSummaryOperation(this).perform();
            } else if (dataTypes == RecordedDataTypes.TYPE_DEBUGLOGS) {
                new HuamiFetchDebugLogsOperation(this).perform();
            } else {
                LOG.warn("fetching multiple data types at once is not supported yet");
            }
        } catch (IOException ex) {
            LOG.error("Unable to fetch recorded data types" + dataTypes, ex);
        }
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {
        boolean handled = super.onCharacteristicChanged(gatt, characteristic);
        if (!handled) {
            UUID characteristicUUID = characteristic.getUuid();
            if (HuamiService.UUID_CHARACTERISTIC_3_CONFIGURATION.equals(characteristicUUID)) {
                return handleConfigurationInfo(characteristic.getValue());
            }
        }
        return false;
    }

    private boolean handleConfigurationInfo(byte[] value) {
        if (value == null || value.length < 4) {
            return false;
        }
        if (value[0] == 0x10 && value[1] == 0x0e && value[2] == 0x01) {
            String gpsVersion = new String(value, 3, value.length - 3);
            LOG.info("got gps version = " + gpsVersion);
            gbDevice.setFirmwareVersion2(gpsVersion);
            return true;
        }
        return false;
    }

    // this probably does more than only getting the GPS version...
    private AmazfitBipSupport requestGPSVersion(TransactionBuilder builder) {
        LOG.info("Requesting GPS version");
        builder.write(getCharacteristic(HuamiService.UUID_CHARACTERISTIC_3_CONFIGURATION), AmazfitBipService.COMMAND_REQUEST_GPS_VERSION);
        return this;
    }

    @Override
    public void phase2Initialize(TransactionBuilder builder) {
        super.phase2Initialize(builder);
        LOG.info("phase2Initialize...");
        setLanguage(builder);
        requestGPSVersion(builder);
    }

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new AmazfitBipFWHelper(uri, context);
    }
}
