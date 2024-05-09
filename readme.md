Copy via ssh the jar file to the Raspberry Pi:

```shell
C:\Users\user>scp "I:\projects\bite-board-bot-java\target\bite-board-bot.jar" yan@192.168.1.114:/home/yan/workspace/bite-board-bot/
yan@192.168.1.114's password:
bite-board-bot.jar                                                                    100%   66KB   1.7MB/s   00:00
```

Any java version >= 8 will work, for example on my Raspberry Pi:

```shell
sudo apt update
sudo apt upgrade
sudo apt install default-jdk
java -version
```

Start the bot with:

```shell
screen -S bite-board-bot -d -m java -jar bite-board-bot.jar
screen -r bite-board-bot
```

https://askubuntu.com/a/124903

Ctrl+a followed by d. Note the lower case.
