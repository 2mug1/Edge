package com.github.iamtakagi.edge;

import java.text.SimpleDateFormat;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;

import java.util.*;

public class Edge extends JavaPlugin {

  private static Edge instance;
  private EdgeConfig config;
  private ScoreboardLibrary scoreboard;
  private Map<UUID, EdgeSidebar> sidebars = new HashMap();

  @Override
  public void onEnable() {
    instance = this;
    this.saveDefaultConfig();
    this.loadConfig();
    this.setupScoreboard();
    this.getServer().getPluginManager().registerEvents(new SidebarListener(), this);
  }

  @Override
  public void onDisable() {
    this.saveDefaultConfig();
    scoreboard.close();
  }

  public static Edge getInstance() {
    return instance;
  }

  public EdgeConfig getEdgeConfig() {
    return config;
  }

  private void loadConfig() {
    this.config = new EdgeConfig((YamlConfiguration) this.getConfig());
  }

  private void setupScoreboard() {
    try {
      scoreboard = ScoreboardLibrary.loadScoreboardLibrary(this);
    } catch (NoPacketAdapterAvailableException e) {
      scoreboard = new NoopScoreboardLibrary();
      this.getLogger().warning("No scoreboard packet adapter available!");
    }
  }

  class EdgeSidebar {
    private Player player;
    private Sidebar sidebar;
    private ComponentSidebarLayout layout;
    private BukkitTask tickTask;

    EdgeSidebar(Player player) {
      this.player = player;
    }

    public void setup() {
      this.sidebar = scoreboard.createSidebar();
      this.sidebar.addPlayer(player);
      this.tickTask = Bukkit.getScheduler().runTaskTimer(instance, this::tick, 0, 20);
    }

    private void tick() {
      SidebarComponent.Builder builder = SidebarComponent.builder();
      SidebarComponent title = SidebarComponent.staticLine(
          Component.text(ChatColor.translateAlternateColorCodes('&', config.getSidebarSettings().getTitle())));
      for (int i = 0; i < config.getSidebarSettings().getLines().size(); i++) {
        String line = config.getSidebarSettings().getLines().get(i);
        if (line.length() > 0) {
          builder.addStaticLine(Component.text(ChatColor.translateAlternateColorCodes('&', parseLine(line))));
        } else {
          builder.addStaticLine(Component.empty());
        }
      }
      SidebarComponent component = builder.build();
      this.layout = new ComponentSidebarLayout(title, component);
      this.layout.apply(this.sidebar);
    }

    private String parseLine(String raw) {
      if (raw.contains("{PING}")) {
        raw = raw.replace("{PING}", "" + player.getPing());
      }

      if (raw.contains("{X}")) {
        raw = raw.replace("{X}", "" + player.getLocation().getBlockX());
      }

      if (raw.contains("{Y}")) {
        raw = raw.replace("{Y}", "" + player.getLocation().getBlockY());
      }

      if (raw.contains("{Z}")) {
        raw = raw.replace("{Z}", "" + player.getLocation().getBlockZ());
      }

      if (raw.contains("{DIRECTION}")) {
        raw = raw.replace("{DIRECTION}", Utils.getDirectionByFace(player.getFacing()));
      }

      if (raw.contains("{WORLD}")) {
        raw = raw.replace("{WORLD}", player.getWorld().getName());
      }

      if (raw.contains("{WORLD_DATE}")) {
        raw = raw.replace("{WORLD_DATE}", TicksFormatter.formatDate(player.getWorld().getFullTime()));
      }

      if (raw.contains("{WORLD_TIME}")) {
        raw = raw.replace("{WORLD_TIME}", TicksFormatter.formatTime(player.getWorld().getTime()));
      }

      if (raw.contains("{WEATHER}")) {
        raw = raw.replace("{WEATHER}", Utils.getWorldWeather(player.getWorld()));
      }

      if (raw.contains("{TPS}")) {
        double[] recetTps = Utils.getRecentTps();
        double avgTps = (recetTps[0] + recetTps[1] + recetTps[2]) / 3;
        raw = raw.replace("{TPS}", "" + (Math.floor(avgTps * 100)) / 100);
      }

      if (raw.contains("{DATE}")) {
        raw = raw.replace("{DATE}",
            new SimpleDateFormat(config.getSidebarSettings().getPatternSettings().getDatePattern())
                .format(Calendar.getInstance().getTime()));
      }

      if (raw.contains("{TIME}")) {
        raw = raw.replace("{TIME}",
            new SimpleDateFormat(config.getSidebarSettings().getPatternSettings().getTimePattern())
                .format(Calendar.getInstance().getTime()));
      }

      if (raw.contains("{USAGE_RAM}")) {
        raw = raw.replace("{USAGE_RAM}", String.format("%,d",
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024));
      }

      if (raw.contains("{TOTAL_RAM}")) {
        raw = raw.replace("{TOTAL_RAM}", String.format("%,d", Runtime.getRuntime().totalMemory() / 1024 / 1024));
      }

      return raw;
    }
  }

  class SidebarListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
      EdgeSidebar sidebar = new EdgeSidebar(event.getPlayer());
      sidebar.setup();
      sidebars.put(event.getPlayer().getUniqueId(), sidebar);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
      sidebars.remove(event.getPlayer().getUniqueId());
    }
  }

  class EdgeConfig {
    private SidebarSettings sidebarSettings;

    EdgeConfig(YamlConfiguration yaml) {
      this.sidebarSettings = new SidebarSettings(yaml);
    }

    public SidebarSettings getSidebarSettings() {
      return this.sidebarSettings;
    }

    class SidebarSettings {
      private String title;
      private List<String> lines;
      private PatternSettings patternSettings;

      SidebarSettings(YamlConfiguration yaml) {
        this.title = yaml.getString("sidebar.title");
        this.lines = yaml.getStringList("sidebar.lines");
        this.patternSettings = new PatternSettings(yaml);
      }

      public String getTitle() {
        return this.title;
      }

      public List<String> getLines() {
        return this.lines;
      }

      public PatternSettings getPatternSettings() {
        return this.patternSettings;
      }

      class PatternSettings {
        private String datePattern;
        private String timePattern;
        private String worldDatePattern;
        private String worldTimePattern;

        PatternSettings(YamlConfiguration yaml) {
          this.datePattern = yaml.getString("sidebar.pattern.date");
          this.timePattern = yaml.getString("sidebar.pattern.time");
          this.worldDatePattern = yaml.getString("sidebar.pattern.world_date");
          this.worldTimePattern = yaml.getString("sidebar.pattern.world_time");
        }

        public String getDatePattern() {
          return this.datePattern;
        }

        public String getTimePattern() {
          return this.timePattern;
        }

        public String getWorldDatePattern() {
          return this.worldDatePattern;
        }

        public String getWorldTimePattern() {
          return this.worldTimePattern;
        }
      }
    }
  }
}

class Utils {
  static String getDirectionByFace(BlockFace face) {
    switch (face) {
      case NORTH:
        return "北";
      case EAST:
        return "東";
      case SOUTH:
        return "南";
      case WEST:
        return "西";
      case UP:
        return "上";
      case DOWN:
        return "下";
      case NORTH_EAST:
        return "北東";
      case NORTH_WEST:
        return "北西";
      case SOUTH_EAST:
        return "南東";
      case SOUTH_WEST:
        return "南西";
      case WEST_NORTH_WEST:
        return "西北西";
      case NORTH_NORTH_WEST:
        return "北北西";
      case NORTH_NORTH_EAST:
        return "北北東";
      case EAST_NORTH_EAST:
        return "東北東";
      case EAST_SOUTH_EAST:
        return "東南東";
      case SOUTH_SOUTH_EAST:
        return "南南東";
      case SOUTH_SOUTH_WEST:
        return "南南西";
      case WEST_SOUTH_WEST:
        return "西南西";
      case SELF:
        return "自分";
      default:
        return "不明";
    }
  }

  static double[] getRecentTps() {
    double[] recentTps = null;
    try {
      Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
      recentTps = ((double[]) server.getClass().getField("recentTps").get(server));
    } catch (ReflectiveOperationException e) {
      e.printStackTrace();
    }
    return recentTps;
  }

  public static String getWorldWeather(World world) {
    if (world.isClearWeather()) {
      return "晴れ";
    } else if (world.isThundering() && !world.isClearWeather()) {
      return "雷雨";
    } else if (!world.isClearWeather() && !world.isThundering()) {
      return "雨";
    }
    return "不明";
  }
}

class TicksFormatter {
  public static final Map<String, Integer> nameToTicks = new LinkedHashMap<String, Integer>();
  public static final Set<String> resetAliases = new HashSet<String>();
  public static final int ticksAtMidnight = 18000;
  public static final int ticksPerDay = 24000;
  public static final int ticksPerHour = 1000;
  public static final double ticksPerMinute = 1000d / 60d;
  public static final double ticksPerSecond = 1000d / 60d / 60d;
  private static final SimpleDateFormat dateSdf = new SimpleDateFormat(
      Edge.getInstance().getEdgeConfig().getSidebarSettings().getPatternSettings().getWorldDatePattern());
  private static final SimpleDateFormat timeSdf = new SimpleDateFormat(
      Edge.getInstance().getEdgeConfig().getSidebarSettings().getPatternSettings().getWorldTimePattern());

  static {
    timeSdf.setTimeZone(TimeZone.getTimeZone(System.getenv("TZ")));

    nameToTicks.put("sunrise", 23000);
    nameToTicks.put("dawn", 23000);

    nameToTicks.put("daystart", 0);
    nameToTicks.put("day", 0);

    nameToTicks.put("morning", 1000);

    nameToTicks.put("midday", 6000);
    nameToTicks.put("noon", 6000);

    nameToTicks.put("afternoon", 9000);

    nameToTicks.put("sunset", 12000);
    nameToTicks.put("dusk", 12000);
    nameToTicks.put("sundown", 12000);
    nameToTicks.put("nightfall", 12000);

    nameToTicks.put("nightstart", 14000);
    nameToTicks.put("night", 14000);

    nameToTicks.put("midnight", 18000);

    resetAliases.add("reset");
    resetAliases.add("normal");
    resetAliases.add("default");
  }

  // ============================================
  public static long parse(String desc) throws NumberFormatException {
    desc = desc.toLowerCase(Locale.JAPAN).replaceAll("[^A-Za-z0-9:]", "");

    try {
      return parseTicks(desc);
    } catch (NumberFormatException e) {
    }

    try {
      return parse24(desc);
    } catch (NumberFormatException e) {
    }

    try {
      return parse12(desc);
    } catch (NumberFormatException e) {
    }

    try {
      return parseAlias(desc);
    } catch (NumberFormatException e) {
    }

    throw new NumberFormatException();
  }

  public static long parseTicks(String desc) throws NumberFormatException {
    if (!desc.matches("^[0-9]+ti?c?k?s?$")) {
      throw new NumberFormatException();
    }

    desc = desc.replaceAll("[^0-9]", "");

    return Long.parseLong(desc) % 24000;
  }

  public static long parse24(String desc) throws NumberFormatException {
    if (!desc.matches("^[0-9]{2}[^0-9]?[0-9]{2}$")) {
      throw new NumberFormatException();
    }

    desc = desc.toLowerCase(Locale.JAPAN).replaceAll("[^0-9]", "");

    if (desc.length() != 4) {
      throw new NumberFormatException();
    }

    final int hours = Integer.parseInt(desc.substring(0, 2));
    final int minutes = Integer.parseInt(desc.substring(2, 4));

    return hoursMinutesToTicks(hours, minutes);
  }

  public static long parse12(String desc) throws NumberFormatException {
    if (!desc.matches("^[0-9]{1,2}([^0-9]?[0-9]{2})?(pm|am)$")) {
      throw new NumberFormatException();
    }

    int hours = 0;
    int minutes = 0;

    desc = desc.toLowerCase(Locale.JAPAN);
    String parsetime = desc.replaceAll("[^0-9]", "");

    if (parsetime.length() > 4) {
      throw new NumberFormatException();
    }

    if (parsetime.length() == 4) {
      hours += Integer.parseInt(parsetime.substring(0, 2));
      minutes += Integer.parseInt(parsetime.substring(2, 4));
    } else if (parsetime.length() == 3) {
      hours += Integer.parseInt(parsetime.substring(0, 1));
      minutes += Integer.parseInt(parsetime.substring(1, 3));
    } else if (parsetime.length() == 2) {
      hours += Integer.parseInt(parsetime.substring(0, 2));
    } else if (parsetime.length() == 1) {
      hours += Integer.parseInt(parsetime.substring(0, 1));
    } else {
      throw new NumberFormatException();
    }

    if (desc.endsWith("pm") && hours != 12) {
      hours += 12;
    }

    if (desc.endsWith("am") && hours == 12) {
      hours -= 12;
    }

    return hoursMinutesToTicks(hours, minutes);
  }

  public static long hoursMinutesToTicks(final int hours, final int minutes) {
    long ret = ticksAtMidnight;
    ret += (hours) * ticksPerHour;

    ret += (minutes / 60.0) * ticksPerHour;

    ret %= ticksPerDay;
    return ret;
  }

  public static long parseAlias(final String desc) throws NumberFormatException {
    final Integer ret = nameToTicks.get(desc);
    if (ret == null) {
      throw new NumberFormatException();
    }

    return ret;
  }

  public static boolean meansReset(final String desc) {
    return resetAliases.contains(desc);
  }

  public static String formatTicks(final long ticks) {
    return (ticks % ticksPerDay) + "ticks";
  }

  public static String formatTime(final long ticks) {
    synchronized (timeSdf) {
      return format(ticks, timeSdf);
    }
  }

  public static String formatDate(final long ticks) {
    synchronized (dateSdf) {
      return format(ticks, dateSdf);
    }
  }

  public static String format(final long ticks, final SimpleDateFormat format) {
    final Date date = ticksToDate(ticks);
    return format.format(date);
  }

  public static Date ticksToDate(long ticks) {
    // Assume the server time starts at 0. It would start on a day.
    // But we will simulate that the server started with 0 at midnight.
    ticks = ticks - ticksAtMidnight + ticksPerDay;

    // How many ingame days have passed since the server start?
    final long days = ticks / ticksPerDay;
    ticks -= days * ticksPerDay;

    // How many hours on the last day?
    final long hours = ticks / ticksPerHour;
    ticks -= hours * ticksPerHour;

    // How many minutes on the last day?
    final long minutes = (long) Math.floor(ticks / ticksPerMinute);
    final double dticks = ticks - minutes * ticksPerMinute;

    // How many seconds on the last day?
    final long seconds = (long) Math.floor(dticks / ticksPerSecond);

    final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(System.getenv("TZ")), Locale.JAPAN);
    cal.setLenient(true);

    // And we set the time to 0! And append the time that passed!
    cal.set(0, Calendar.JANUARY, 1, 0, 0, 0);
    cal.add(Calendar.DAY_OF_YEAR, (int) days);
    cal.add(Calendar.HOUR_OF_DAY, (int) hours);
    cal.add(Calendar.MINUTE, (int) minutes);
    cal.add(Calendar.SECOND, (int) seconds + 1); // To solve rounding errors.

    return cal.getTime();
  }
}