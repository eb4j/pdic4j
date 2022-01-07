/*
 * PDIC4j, a PDIC dictionary access library.
 * Copyright (C) 2021 Hiroshi Miura.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eb4j.pdic;

/**
 * @author wak (Apache-2.0)
 * @author Hiroshi Miura
 */
public final class PdicElement {
    private final byte attr;
    private final String index;
    private final String disp;
    private final String trans;
    private final String sample;
    private final String phone;

    private PdicElement(final byte attr, String index, String disp, String trans, String sample, String phone) {
        this.attr = attr;
        this.index = index;
        this.disp = disp;
        this.trans = trans;
        this.sample = sample;
        this.phone = phone;
    }

    public byte getAttr() {
        return attr;
    }

    public String getIndex() {
        return index;
    }

    public String getDisp() {
        return disp;
    }

    public String getTrans() {
        return trans;
    }

    public String getSample() {
        return sample;
    }

    public String getPhone() {
        return phone;
    }

    public static final class PdicElementBuilder {
        private byte attr = 0;
        private String index = null;
        private String disp = null;
        private String trans = null;
        private String sample = null;
        private String phone = null;

        public void setAttr(byte attr) {
            this.attr = attr;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public void setDisp(String disp) {
            this.disp = disp;
        }

        public void setTrans(String trans) {
            this.trans = trans;
        }

        public void setSample(String sample) {
            this.sample = sample;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public PdicElement build() {
            return new PdicElement(attr, index, disp, trans, sample, phone);
        }
    }
}

