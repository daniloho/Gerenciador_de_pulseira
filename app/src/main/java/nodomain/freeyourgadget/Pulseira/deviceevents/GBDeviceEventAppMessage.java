/*  Copyright (C) 2015-2019 Andreas Shimokawa, Carsten Pfeiffer

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
package nodomain.freeyourgadget.Pulseira.deviceevents;

import java.util.UUID;

public class GBDeviceEventAppMessage extends GBDeviceEvent {
    public static int TYPE_APPMESSAGE = 0;
    public static int TYPE_ACK = 1;
    public static int TYPE_NACK = 2;

    public int type;
    public UUID appUUID;
    public int id;
    public String message;

    @Override
    public String toString() {
        return "GBDeviceEventAppMessage{" +
                "type=" + type +
                ", appUUID=" + appUUID +
                ", message='" + message + '\'' +
                ", id=" + id +
                '}';
    }
}