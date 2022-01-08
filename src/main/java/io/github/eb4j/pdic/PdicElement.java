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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PDic dictionary entry data class.
 * This is POJO data class to hold search result entry.
 * @author wak (Apache-2.0)
 * @author Hiroshi Miura
 */
public final class PdicElement {
    private final byte attribute;
    private final String indexWord;
    private final String headWord;
    private final String translation;
    private final String example;
    private final String pronunciation;

    private PdicElement(final byte attribute, final String indexWord, final String headWord, final String translation,
                        final String example, final String pronunciation) {
        this.attribute = attribute;
        this.indexWord = indexWord;
        this.headWord = headWord;
        this.translation = translation;
        this.example = example;
        this.pronunciation = pronunciation;
    }

    /**
     * Attribute of entry. (for internal)
     * @return attribute flag.
     */
    byte getAttribute() {
        return attribute;
    }

    /**
     * Get indexed word of entry.
     * @return indexed word.
     */
    public @NotNull String getIndexWord() {
        return indexWord;
    }

    /**
     * Get heading word of entry.
     * @return head word.
     */
    public @NotNull String getHeadWord() {
        return headWord;
    }

    /**
     * Get translation clause.
     * @return clause when exist, otherwise null.
     */
    public @Nullable String getTranslation() {
        return translation;
    }

    /**
     * Get example sentences.
     * @return sentences when exist, otherwise null.
     */
    public @Nullable String getExample() {
        return example;
    }

    /**
     * Get pronounciations.
     * @return pronounciation in phonetic code when exist, otherwise null.
     */
    public @Nullable String getPronunciation() {
        return pronunciation;
    }

    static final class PdicElementBuilder {
        private byte attr = 0;
        private String index = null;
        private String disp = null;
        private String trans = null;
        private String sample = null;
        private String phone = null;

        public void setAttr(final byte attr) {
            this.attr = attr;
        }

        public void setIndex(final String index) {
            this.index = index;
        }

        public void setDisp(final String disp) {
            this.disp = disp;
        }

        public void setTrans(final String trans) {
            this.trans = trans;
        }

        public void setSample(final String sample) {
            this.sample = sample;
        }

        public void setPhone(final String phone) {
            this.phone = phone;
        }

        public PdicElement build() {
            return new PdicElement(attr, index, disp, trans, sample, phone);
        }
    }
}

