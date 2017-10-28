# NameTagChanger
NameTagChanger is a replacement for the old TagAPI. If you're looking to change player names in versions before 1.8, I recommend checking
out [TagAPI](https://dev.bukkit.org/projects/tag).

## What is it and what does it do?
NameTagChanger is a library to change the name's above player's heads. As an added bonus, it also allows changing of skins in the later
versions!

There is however one limitation to the names - they can only be sixteen characters long. If you only need to prefix or suffix something to
a player name, I recommend using Scoreboard Teams, which will allow you an extra 16 characters on both the prefix and suffix. This can
also be combined in with NameTagChanger to completely change the original name, resulting in 48 completely customizable characters for
player names.

### bStats
NameTagChanger uses Metrics on https://bstats.org/. This can be turned off like any other bStats metrics by going to _/plugins/bStats/config.yml_.
More information can be found on bStats' ['Getting Started' page](https://bstats.org/getting-started).

You should always inform your users that NameTagChanger sends data to bStats if you include it in one of your plugins.

### ProtocolLib
NameTagChanger uses packets to do what it does. The way NameTagChanger is programmed makes it so that it can support ProtocolLib, but
doesn't require it. It basically checks if ProtocolLib is installed, and if it is, it uses a special [ProtocolLibPacketHandler](https://github.com/Alvin-LB/NameTagChanger/blob/master/src/main/java/com/bringholm/nametagchanger/ProtocolLibPacketHandler.java), but
otherwise it falls back to a [custom channel injector](https://github.com/Alvin-LB/NameTagChanger/blob/master/src/main/java/com/bringholm/nametagchanger/ChannelPacketHandler.java).

The ProtocolLib implementation is more reliable (although I have done my best to ensure that both work properly!), so it is recommended
you tell your users that they should install ProtocolLib if they want everything to work as smoothly as possible.

You should also add a `softdepend: [ProtocolLib]` in your `plugin.yml` to ensure correct load order if ProtocolLib is installed.

## How do I use it in my plugin?
NameTagChanger is not a stand-alone plugin like TagAPI was. Instead it is designed to be bundled together with your plugin, in a process
called shading. Following are some instructions on how to shade NameTagChanger in a few environments:

### Maven
<details><summary>Click to show</summary>

If you happen to be using maven, shading is rather simple. First you need to add the following dependency and repository:
```xml
<repositories>
    <repository>
        <id>alvinb-repo</id>
        <url>http://repo.bringholm.com/</url>
    </repository>
</repositories>
<dependencies>
   <dependency>
       <groupId>com.bringholm.nametagchanger</groupId>
       <artifactId>NameTagChanger</artifactId>
       <version>1.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```
And to shade it into your jar, add this to the `<build>` section of your POM:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.0.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <artifactSet>
                            <includes>
                                <include>com.bringholm.nametagchanger:NameTagChanger</include>
                            </includes>
                        </artifactSet>
                    </configuration>
                </execution>
            </executions>
        </plugin>
     </plugins>
</build>
```
</details>

### IntelliJ IDEA
<details><summary>Click to show</summary>

1. Open the Project Structure menu by pressing Ctrl + Alt + Shift + S
2. Choose "Modules" in the menu from the right
3. Click the green plus on the right side of the window and select "JARs or directories"
4. Select the [NameTagChanger jar you downloaded](#downloads), click OK
5. Set the scope to "Compile"
![](https://i.imgur.com/aFfBVQq.png)
</details>

### Eclipse
<details><summary>Click to show</summary>

To my knowledge, it is not possible to shade libraries into a non-executable jar using standard eclipse IDE tools.
We're going to have to use the [Fat Jar Eclipse Plugin](http://fjep.sourceforge.net/).

Since the Fat Jar eclipse plugin is such an old plugin we first need to install a compatibility plugin. This plugin is called 'Eclipse 2.0 Style Plugin Support'. We download it by doing the following:
- Go to Help > Install new software
- Paste 'http://download.eclipse.org/eclipse/updates/4.6' in the 'type or select a site' field.
- Click 'Add' and choose a name in the dialogue box (this name doesn't matter).
- Now you should have a bunch of options in the pane below the 'type or select a site' text field.
- Click the little arrow next to 'Eclipse Tests, Tools, Examples, and Extras'
- Check the 'Eclipse 2.0 Style Plugin Support' checkbox and finish the installation.
![](https://i.imgur.com/rT3u6P9.png)
After this, we need to install the actual Fat Jar plugin. The process is pretty much the same as with the previous plugin, only difference is that we use the 'http://kurucz-grafika.de/fatjar' url.
![](https://i.imgur.com/i3tYmcC.png)
Next, we need to add NameTagChanger to our buildpath by right clicking on our project in the Project Explorer, selecting properties, and click Add JARs/Add External JARs and locating the [NameTagChanger jar we downloaded](#downloads).
After that, we want to export our code and shade NameTagChanger in with it at the same time, which we do like this:
- Go to File > Export
- Select the 'Fat Jar Exporter' in the 'Other' folder.
- Hit 'Next' and select the java project containing your code.
- Hit 'Next' and choose a name for your final jar file.
- Hit 'Next' and select your project file.
- Hit 'Finish' and your jar will be saved in the project directory, ready to be run as a plugin!
![](https://i.imgur.com/lOs78z4.png)
</details>

### Dynamic downloading
For those of you who are worried about jars getting too big (you really shouldn't, but whatever), it is entirely possible to programatically
download this from one of the [download links](#downloads), and then use a custom classloader to load it.

## Okay, now I have it setup in my project, what do all of the methods do?
If you are having difficulties understanding this documentation, you might want to read the [JavaDocs](https://bringholm.com/javadocs/nametagchanger/com/bringholm/nametagchanger/NameTagChanger.html).

### Names
<details><summary>Click to show</summary>

All you need to do to change a player's name is the following:
```java
Player player = Bukkit.getPlayer("AlvinB");
NameTagChanger.INSTANCE.changePlayerName(player, "jeb_");
```
Resetting a player name is equally as simple:
```java
Player player = Bukkit.getPlayer("AlvinB");
NameTagChanger.INSTANCE.resetPlayerName(player);
```
You can get a player's current changed name like this:
```java
Player player = Bukkit.getPlayer("AlvinB");
NameTagChanger.INSTANCE.getChangedName(player);
```
If a player does not have a changed name, the above method will return null.
</details>

### Getting skins
<details><summary>Click to show</summary>

Skins are a little bit more complicated, but not by a whole lot.

The skins are managed using the 'Skin' object. There are a few ways to obtain a Skin Object. You can:
#### Get a Skin from an online player
```java
Player player = Bukkit.getPlayer("AlvinB");
NameTagChanger.INSTANCE.getDefaultSkinFromPlayer(player);
```
This method only works if the server has loaded the skin.

#### Get a Skin from a config
The Skin object implements ConfigurationSerializable which means that you can load and save it to configs. This allows for a multitude of
options, including saving and loading between restarts and bundling skins with your plugin.
```java
Skin skin = (Skin) getConfig().get("my-saved-skin");
```

#### Get a Skin from Mojang's servers
NameTagChanger has the functionality to request a skin from Mojang's API servers. This allows you to get the skin of any player, as
long as you know their username or UUID. Because the requests are made asynchronously to the server, a call back system is implemented
using the SkinCallBack class. The SkinCallBack class also allows you to handle any potential errors that may occur when fetching the skin.
Note that the call back is always fired on the main thread, even if the request failed.
```java
NameTagChanger.INSTANCE.getSkin("AlvinB", new SkinCallBack() {
    @Override
    public void callBack(Skin skin, boolean successful, Exception exception) {
        if (successful) {
            // Do our stuff with the skin!
            getLogger().info("Wohoo! We got the skin! " + skin);
        } else {
            getLogger().log(Level.WARNING, "Couldn't get skin :(", exception);
        }
    }
});
```

#### Use the default skin
The default skin is either Alex or Steve depending on the User's UUID. Odd UUIDs will be Alex, and even ones will be Steve.
In NameTagChanger, the default skin is represented as `Skin.EMPTY_SKIN`, and will be returned by methods such as `getSkin()` and
`getDefaultSkinFromPlayer()`, if the profile/player has no available skin.
```java
Skin.EMPTY_SKIN
```
</details>

### Setting skins to players
<details><summary>Click to show</summary>

Once you have the Skin instances, you are going to want to set them to the players. This works in a very similar way to the names.
The only difference is that you are required to call the `updatePlayer()` method for the changes to take effect.

To set a skin:
```java
Player player = Bukkit.getPlayer("AlvinB");
NameTagChanger.INSTANCE.setPlayerSkin(player, Skin.EMPTY_SKIN); // Skin.EMPTY_SKIN can of course be any other skin instance you have
NameTagChanger.INSTANCE.updatePlayer(player); // Update the player so the changes actually take effect
```
To reset a skin:
```java
Player player = Bukkit.getPlayer("AlvinB");
NameTagChanger.INSTANCE.resetPlayerSkin(player);
NameTagChanger.INSTANCE.updatePlayer(player); // Update the player so the changes actually take effect
```
You can also get a player's currently changed skin like this:
```java
Player player = Bukkit.getPlayer("AlvinB");
NameTagChanger.INSTANCE.getChangedSkin(player);
```
The above method will return null if the player's skin isn't changed.
</details>

### General methods
<details><summary>Click to show</summary>

The NameTagChanger class has several methods for controlling how NameTagChanger operates.

### Enable and Disable
The `enable()` and `disable()`methods controls whether or not NameTagChanger is enabled or disabled. The `enable()` method registers all
the packet listeners and other stuff to make NameTagChanger work. The `disable()` method does the exact opposite of this and unregisteres
them.

You should always try to enable and disable NameTagChanger in your `onEnable()` and `onDisable()` methods to make everything work properly.
Just make sure to check whether NameTagChanger is already disabled/enabled using `isEnabled()`. Not doing so will cause exceptions to be thrown.
```java
@Override
public void onEnable() {
    if (!NameTagChanger.INSTANCE.isEnabled()) {
        NameTagChanger.INSTANCE.enable();
    }
}

@Override
public void onDisable() {
    if (NameTagChanger.INSTANCE.isEnabled()) {
        NameTagChanger.INSTANCE.disable();
    }
}
```

### Setting plugin instance.
Because NameTagChanger is not a stand-alone plugin, it needs somewhere to register Bukkit tasks and events to. NameTagChanger will do
some trickery to try and automatically detect which plugin it is bundled inside, but if this fails, you will need to set the plugin instance
manually. This can be done using the `setPlugin()` method.
```java
NameTagChanger.INSTANCE.setPlugin(myPlugin);
```
Which plugin instance you set it to doesn't really matter, it is only important that it is an enabled plugin that tasks and events can
be registered to.
</details>

## Downloads
* [v1.0-SNAPSHOT](https://bringholm.com/downloads/NameTagChanger%20v1.0-SNAPSHOT.jar)
* [v1.0](http://bringholm.com/repo/com/bringholm/nametagchanger/NameTagChanger/1.0/NameTagChanger-1.0.jar)
* [v1.1-SNAPSHOT](https://bringholm.com/downloads/NameTagChanger%20v1.1-SNAPSHOT.jar)
