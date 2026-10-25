package com.termux.shared.terminal.io.extrakeys;

import androidx.annotation.DrawableRes;
import com.termux.shared.R;
import java.util.HashMap;
import java.util.Map;

/** NGTerminal: Material Symbols Rounded icon per extra-key name. 0 = no icon (draw the text label as before). */
public final class ExtraKeyIcons {
    private static final Map<String, Integer> ICONS = new HashMap<>();
    static {
        ICONS.put("HOME", R.drawable.ic_ng_key_first_page);
        ICONS.put("END", R.drawable.ic_ng_key_last_page);
        ICONS.put("UP", R.drawable.ic_ng_key_keyboard_arrow_up);
        ICONS.put("DOWN", R.drawable.ic_ng_key_keyboard_arrow_down);
        ICONS.put("LEFT", R.drawable.ic_ng_key_keyboard_arrow_left);
        ICONS.put("RIGHT", R.drawable.ic_ng_key_keyboard_arrow_right);
        ICONS.put("TAB", R.drawable.ic_ng_key_keyboard_tab);
        ICONS.put("CTRL j", R.drawable.ic_ng_key_keyboard_return); // the user's new-line macro
        ICONS.put("KEYBOARD", R.drawable.ic_ng_key_keyboard);
        ICONS.put("WAKELOCK", R.drawable.ic_ng_key_coffee);
    }
    private ExtraKeyIcons() {}

    @DrawableRes
    public static int iconFor(String key) {
        if (key == null) return 0;
        Integer id = ICONS.get(key);
        return id == null ? 0 : id;
    }
}
