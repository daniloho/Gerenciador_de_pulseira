package nodomain.freeyourgadget.Pulseira.service.devices.huami.amazfitgts;

import nodomain.freeyourgadget.Pulseira.service.devices.huami.miband3.MiBand3Support;

public class AmazfitGTSSupport extends MiBand3Support {
    @Override
    public byte getCryptFlags() {
        return (byte) 0x80;
    }

}
