# UltimateHotbars

<img width="954" height="646" alt="2025-08-01_18 42 08" src="https://github.com/user-attachments/assets/c4915706-dd42-4dd4-a9c3-57c3a2ad357c" />
<img width="954" height="646" alt="2025-08-01_18 42 34" src="https://github.com/user-attachments/assets/b65cbc38-751e-4eb1-a8d0-67463d239686" />
<img width="954" height="646" alt="2025-08-01_18 42 13" src="https://github.com/user-attachments/assets/ddf5fe5a-c68f-48a5-ae8f-0d44c4151149" />
<img width="954" height="646" alt="2025-08-01_18 42 19" src="https://github.com/user-attachments/assets/515ec121-3382-4dab-864f-e2d4e5a233d1" />
<img width="954" height="646" alt="2025-08-01_18 42 50" src="https://github.com/user-attachments/assets/6a11f979-caf2-41f6-9e71-2d756262f76a" />
<img width="954" height="646" alt="2025-08-01_18 43 05" src="https://github.com/user-attachments/assets/896a3970-c31e-4fe2-8bde-797a821f824d" />

**UltimateHotbars** is a Minecraft Forge mod (1.20.1 / Forge) that gives you virtually unlimited custom hotbars and pages. Easily switch between them in-game or in any inventory screen, save and restore layouts, and keep your tools, and resources organized!

This is the new Version 2 of the mod. Version 1 will no longer be worked on or updated so please switch to the V2 branch/version.
In the config menu hit the Purge Data Files button if your comming from V1.x.x as the hot bar data files are not compatable and it will be all messed up. 
(Sorry you will have to start fresh with your Hot bars.)

---

## 🌟 Features

- **Virtual Hotbars & Pages**  
  Create up to 100 hotbars per page (configurable), and as many pages as you like.  
- **Seamless Switching**  
  Switch hotbars or pages via keybinds or mouse wheel—even inside GUIs and containers.  
- **Persistent Storage**  
  Your layouts are automatically saved and loaded between sessions.  
- **Quick Clear**  
  Instantly clear the current hotbar with a single key.  
- **Configurable Sounds & HUD**  
  Optional click sounds, on-screen page labels, debug overlay, and color themes.  


---

## 🎮 Default Keybindings

| Action                           | Key(s)              |
|----------------------------------|---------------------|
| Open/Close Hotbar GUI            | `h`                 |
| ⬆️ Next Hotbar                   | `=`                 |
| ⬇️ Previous Hotbar               | `-`                 |
| ➡️ Next Page (Ctrl + `=`)        | `Ctrl + =`          |
| ⬅️ Previous Page (Ctrl + `-`)    | `Ctrl + -`          |
| ❌ Clear Current Hotbar          | `Delete`            |

> **Tip:** You can rebind all of these in the Controls menu.

---

## ⚙️ Configuration Options

Accessible via the in-game **Config** button in the Hotbar GUI, or by editing `config/ultimatehotbar-client.toml`.

| Option                          | Default   | Description                                                                               |
|---------------------------------|-----------|-------------------------------------------------------------------------------------------|
| **enableSounds**                | `true`    | Play a click/drum sound when switching hotbars or pages.                                  |
| **maxHotbarsPerPage**           | `20`      | How many hotbars each page can hold (1–100).                                              |
| **showDebugOverlay**            | `false`   | Draws extra debug info (for troubleshooting).                                             |
| **showHudLabel**                | `true`    | Display “Pg 1/3” style labels on your HUD.                                                |
| **showHudLabelBackground**      | `true`    | Enable a background behind the HUD label text.                                            |
| **highlightColor**              | `[1,1,0,0.8]` | RGBA color for the selected hotbar highlight in the GUI.                                  |
| **hudLabelBackgroundColor**     | `[0,0,0,0.5]` | RGBA color for the HUD label background.                                                 |
| **hudLabelTextColor**           | `[1,1,1,1]`   | RGBA color for the HUD label text.                                                       |
| **hoverBorderColor**            | `[1,1,1,1]`   | RGBA color for the slot-hover border in the GUI.                                         |
| **scrollThrottleMs**            | `50`          | delay (10–150 ms) between scroll/key-hold repeats to avoid duplicates.            |

- **Scroll Throttle**  
  Adjustable delay to prevent duplicate/overwrite when scrolling quickly. you can lower the delay if you really want to but it might mess up your hotbars.
  If you are getting hot bars copying over eachother then raise this number but default should be ok for most systems this was put in just incase your scrolling faster then your systems I/O.

---

## 💡 Usage

1. **Open the Hotbar GUI** with your configured key (default `h`).  
2. **Add / Remove Pages** or **Hotbars** via the buttons on the left.  
3. **Drag & Drop** items in the GUI to rearrange.  
4. **Scroll** or use (default -,=) or  **⬆️/⬇️ keys** to switch hotbars.  
5. **Scroll** over page list or **Ctrl + -/=** to flip pages.  
6. **Delete** key clears the current hotbar instantly.  
7. **Close GUI** with `Esc` key.

All changes are auto-saved as you edit—no manual steps required.

## License

This project is licensed under the MIT License—see [LICENSE](LICENSE) for details.
