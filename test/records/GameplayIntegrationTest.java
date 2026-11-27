package records;

import engine.*;
import entity.*;
import screen.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Exercises real collision handlers against the current course gameplay. */
public final class GameplayIntegrationTest {
    private static int checks;
    private static Object get(Object target, String name) throws Exception {
        Field f = field(target.getClass(), name); return f.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static void call(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target);
    }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(Paths.get("out"), "gameplay-integration-");
        Field global = Core.class.getDeclaredField("recordsService"); global.setAccessible(true);
        Object previous = global.get(null);
        RecordsService service = new RecordsService(directory.resolve("profile.properties"));
        global.set(null, service);
        try {
            for (String path : Arrays.asList("bullet", "fragment")) campaign(service, path);
            infinite(service);
            wavesAcrossStages();
            hitsWithoutDefeat();
            menusAndResults();
            timingAndAbort(service);
            service.startRun("abandoned"); service.abandonRun("abandoned");
            check(!service.enemyDefeated("abandoned", "late"), "abandoned run rejects late events");
            check(service.getRecordCount() == 2,
                    "only the infinite run ended with Escape after playing and the natural death are recorded");
        } finally { global.set(null, previous); service.close(); }
        System.out.println("GameplayIntegrationTest passed: " + checks + " checks.");
    }
    private static boolean[] keys() throws Exception {
        Field f = InputManager.class.getDeclaredField("keys"); f.setAccessible(true);
        return (boolean[])f.get(null);
    }
    private static void menusAndResults() throws Exception {
        // 3 of 12 bullets hit, though 5 enemies fell (grenades defeat without a hit).
        ScoreScreen accurate = new ScoreScreen(448,520,60,new GameState(1,100,0,12,5),
                false,Collections.<AchievementUnlock>emptyList(),new RunStats(1,1,12,3,0,0,1));
        check(Math.abs((Double)get(accurate,"accuracy")-0.25)<1e-9,
                "score screen shows hits over shots, as the record does");
        ScoreScreen noStats = new ScoreScreen(448,520,60,new GameState(1,100,0,12,5),
                false,Collections.<AchievementUnlock>emptyList());
        check((Double)get(noStats,"accuracy")==0, "score screen without run statistics shows no accuracy");
        for (boolean infinite : new boolean[]{false,true}) {
            ScoreScreen score = new ScoreScreen(448,520,60,new GameState(1,100,0,1,1),
                    infinite,Arrays.asList(new AchievementUnlock(Achievement.FIRST_ENEMY,1)));
            set(score,"inputDelay",Core.getCooldown(0)); set(score,"isNewRecord",false);
            keys()[java.awt.event.KeyEvent.VK_SPACE]=true;
            try { call(score,"update"); }
            finally { Arrays.fill(keys(),false); }
            check((Integer)get(score,"returnCode")== (infinite ? 8 : 2),"results replay preserves mode");
            check(!(Boolean)get(score,"gameCleared"),"failed or infinite run is not campaign cleared");
            javax.imageio.ImageIO.write((java.awt.image.BufferedImage)get(Core.getDrawManager(),"backBuffer"),
                    "png",Paths.get("out",infinite ? "infinite-results.png" : "campaign-results.png").toFile());
        }
        ScoreScreen cleared = new ScoreScreen(448,520,60,
                new GameState(campaign.CampaignStageData.getTotalStages()+1,500,3,0,20),
                false,firstTiers(Achievement.FIRST_RUN,Achievement.FIRST_ENEMY,Achievement.TWENTY_ENEMIES,
                        Achievement.SIXTY_SECONDS,Achievement.BEAT_HIGH_SCORE));
        call(cleared,"draw");
        check((Boolean)get(cleared,"gameCleared"),"campaign victory retained with achievement list");
        javax.imageio.ImageIO.write((java.awt.image.BufferedImage)get(Core.getDrawManager(),"backBuffer"),
                "png",Paths.get("out","campaign-cleared-results.png").toFile());
    }
    private static java.util.List<AchievementUnlock> firstTiers(final Achievement... achievements) {
        java.util.List<AchievementUnlock> unlocks=new java.util.ArrayList<AchievementUnlock>();
        for (Achievement achievement : achievements) unlocks.add(new AchievementUnlock(achievement,1));
        return unlocks;
    }
    private static void timingAndAbort(RecordsService service) throws Exception {
        service.startRun("timing"); RunSession session=new RunSession("timing", RunMode.CAMPAIGN);
        InfiniteGameScreen game=new InfiniteGameScreen(new GameState(1,0,3,0,0),448,520,60,session);
        game.initialize(); call(game,"update");
        check(session.getActiveDurationMillis(System.nanoTime())==0,"infinite input delay excluded");
        set(game,"inputDelay",Core.getCooldown(0));
        session.startActiveSegment(System.nanoTime()-60001000000L);
        call(game,"update");
        check(service.getUnlockTime(Achievement.SIXTY_SECONDS)!=null,"infinite active timer unlocks sixty seconds");
        keys()[java.awt.event.KeyEvent.VK_ESCAPE]=true;
        try { call(game,"update"); }
        finally { Arrays.fill(keys(),false); }
        long stopped=session.getActiveDurationMillis(System.nanoTime());
        check(!game.isAbandoned(),"Escape after playing ends the infinite run instead of abandoning it");
        check(session.getActiveDurationMillis(System.nanoTime()+5000000000L)==stopped,"Escape stops active timing");
        RunRecord escaped=session.finish(250,7,System.currentTimeMillis());
        check(escaped.getActiveDurationMillis()>=60000,"the escaped run keeps its play time");
        check(service.completeRun(escaped),"Escape after playing creates a completed record, as Core does");
        check(service.getRecordCount()==1,"the escaped infinite run is recorded");
        check(!service.enemyDefeated("timing","late"),"events after the escaped run ended are ignored");
        service.startRun("instant"); RunSession instantSession=new RunSession("instant", RunMode.CAMPAIGN);
        InfiniteGameScreen instant=new InfiniteGameScreen(new GameState(1,0,3,0,0),448,520,60,instantSession);
        instant.initialize(); set(instant,"inputDelay",Core.getCooldown(0));
        keys()[java.awt.event.KeyEvent.VK_ESCAPE]=true;
        try { call(instant,"update"); }
        finally { Arrays.fill(keys(),false); }
        check(instant.isAbandoned(),"Escape before any active play abandons the run");
        check(!service.enemyDefeated("instant","late"),"the abandoned run rejects later events");
        check(service.getRecordCount()==1,"a run left before playing leaves no record");
        service.startRun("death"); RunSession deadSession=new RunSession("death", RunMode.CAMPAIGN);
        InfiniteGameScreen dead=new InfiniteGameScreen(new GameState(1,100,0,2,1),448,520,60,deadSession);
        dead.initialize(); set(dead,"inputDelay",Core.getCooldown(0)); call(dead,"update");
        long duration=deadSession.getActiveDurationMillis(System.nanoTime());
        check(!dead.isAbandoned() && (Boolean)get(dead,"gameOver"),"natural death is eligible for completion");
        check(deadSession.getActiveDurationMillis(System.nanoTime()+5000000000L)==duration,"end animation excluded");
        RunRecord record=deadSession.finish(100,1,System.currentTimeMillis());
        check(service.completeRun(record),"natural infinite death creates completed record");
    }
    @SuppressWarnings("unchecked")
    private static void campaign(RecordsService service, String path) throws Exception {
        service.startRun(path);
        RunSession session = new RunSession(path, RunMode.CAMPAIGN);
        GameScreen game = new GameScreen(new GameState(7,0,3,0,0),
                new GameSettings(1,1,1000,10000),false,448,520,60,
                // The Standard ship can hold the Energy Shield, which the
                // shield-absorption check below needs.
                ShipType.STANDARD,session);
        game.initialize(); set(game,"inputDelay",Core.getCooldown(0));
        Ship player = (Ship)get(game,"ship");
        EnemyShipFormation formation = (EnemyShipFormation)get(game,"enemyShipFormation");
        EnemyShip normal = formation.iterator().next();
        normal.setPositionX(player.getPositionX()); normal.setPositionY(150);
        EnemyShip special = new EnemyShip(); special.setPositionX(player.getPositionX()); special.setPositionY(80);
        set(get(game,"bonusEnemyManager"),"bonusShip",special);
        final List<String> events = new ArrayList<String>();
        game.getGameplayEvents().subscribe(event.EnemyDefeatedEvent.class,
                new event.GameplayEventListener<event.EnemyDefeatedEvent>() {
                    @Override
                    public void onEvent(final event.EnemyDefeatedEvent e) { events.add(e.getEventId()); }
                });
        player.setShieldActive(true);
        Set<Bullet> incoming = (Set<Bullet>)get(game,"bullets");
        incoming.add(BulletPool.getBullet(player.getPositionX()+4,player.getPositionY()+4,4));
        call(game,"manageCollisions");
        check(events.isEmpty() && game.getGameState().getLivesRemaining()==3,
                path+" shield absorption is not an enemy defeat");
        player.setShieldActive(false);
        if (path.equals("bullet")) {
            Set<Bullet> bullets = (Set<Bullet>)get(game,"bullets");
            bullets.add(BulletPool.getBullet(normal.getPositionX()+4,normal.getPositionY()+4,-4));
            bullets.add(BulletPool.getBullet(special.getPositionX()+4,special.getPositionY()+4,-4));
            call(game,"manageCollisions"); call(game,"manageCollisions");
        } else {
            Set<GrenadeFragment> fragments = (Set<GrenadeFragment>) get(game,"fragments");
            for (EnemyShip target : Arrays.asList(normal, special))
                fragments.addAll(GrenadeFragment.explode(target.getPositionX() + target.getWidth() / 2,
                        target.getPositionY() + target.getHeight() / 2));
            call(game,"manageGrenades"); call(game,"manageGrenades");
        }
        check(events.size()==2, path+" reports exactly two enemy defeats");
        check(new HashSet<String>(events).size()==2, path+" supplies distinct event IDs");
        check(game.getGameState().getShipsDestroyed()==2, path+" preserves upstream kill totals");
        set(game,"levelFinished",true);
        call(game,"manageGrenades"); call(game,"manageCollisions");
        check(events.size()==2,path+" does not count kills after the stage ends");
        RunStats stats = session.getStats();
        check(stats.getHits()==(path.equals("bullet") ? 2 : 0),
                path+" session counts bullet hits, not grenade defeats");
        check(stats.getTimesHit()==0, path+" shield absorption is not a player hit");
        check(stats.getStageReached()==7 && stats.getWavesReached()==1, path+" session sees the stage and its wave");
    }
    /** A bullet that hits counts even when the target survives the hit. */
    @SuppressWarnings("unchecked")
    private static void hitsWithoutDefeat() throws Exception {
        RunSession session = new RunSession("hits", RunMode.CAMPAIGN);
        // Stage 7 is a boss stage.
        GameScreen game = new GameScreen(new GameState(7,0,3,0,0),
                new GameSettings(1,1,1000,10000),false,448,520,60,ShipType.STANDARD,session);
        game.initialize(); set(game,"inputDelay",Core.getCooldown(0));
        EnemyShipFormation formation = (EnemyShipFormation)get(game,"enemyShipFormation");
        for (EnemyShip enemy : new ArrayList<EnemyShip>(toList(formation)))
            formation.destroy(enemy);
        final List<String> defeats = new ArrayList<String>();
        game.getGameplayEvents().subscribe(event.EnemyDefeatedEvent.class,
                new event.GameplayEventListener<event.EnemyDefeatedEvent>() {
                    @Override
                    public void onEvent(final event.EnemyDefeatedEvent e) { defeats.add(e.getEventId()); }
                });
        Set<Bullet> bullets = (Set<Bullet>)get(game,"bullets");
        Method bossOnScreen = GameScreen.class.getDeclaredMethod("getBossOnScreen");
        bossOnScreen.setAccessible(true);
        Boss boss = (Boss)bossOnScreen.invoke(game);
        check(boss != null, "stage 7 has a boss");
        bullets.add(BulletPool.getBullet(boss.getPositionX()+boss.getWidth()/2,
                boss.getPositionY()+boss.getHeight()/2,-4));
        call(game,"manageCollisions");
        check(!boss.isDefeated() && session.getStats().getHits()==1, "a bullet on a surviving boss is a hit");

        List<EnemyShip> minions = (List<EnemyShip>)get(game,"summonedEnemies");
        EnemyShip minion = new EnemyShip(40,300,DrawManager.SpriteType.EnemyShipA1,2);
        minions.add(minion);
        bullets.add(BulletPool.getBullet(minion.getPositionX()+4,minion.getPositionY()+4,-4));
        call(game,"manageCollisions");
        check(!minion.isDestroyed() && session.getStats().getHits()==2, "a bullet on an enemy with health left is a hit");

        bullets.add(BulletPool.getBullet(5,5,-4));
        call(game,"manageCollisions");
        check(session.getStats().getHits()==2, "a bullet that hits nothing is not a hit");
        check(defeats.isEmpty(), "none of these hits was a defeat");
    }
    private static List<EnemyShip> toList(final Iterable<EnemyShip> ships) {
        List<EnemyShip> list = new ArrayList<EnemyShip>();
        for (EnemyShip ship : ships) list.add(ship);
        return list;
    }
    /** Waves add up across the screens of one run; the highest stage is kept. */
    private static void wavesAcrossStages() throws Exception {
        RunSession session = new RunSession("waves", RunMode.STORY);
        GameScreen first = new GameScreen(new GameState(2,0,3,0,0),
                Arrays.asList(new GameSettings(1,1,1000,10000), new GameSettings(1,1,1000,10000)),
                false,448,520,60,ShipType.STANDARD,session);
        first.initialize();
        check(session.getStats().getWavesReached()==1, "the first wave starts with the stage");
        call(first,"startNextWave");
        check(session.getStats().getWavesReached()==2, "the next wave is counted when it starts");
        GameScreen second = new GameScreen(new GameState(3,0,3,0,0),
                new GameSettings(1,1,1000,10000),false,448,520,60,ShipType.STANDARD,session);
        second.initialize();
        check(session.getStats().getWavesReached()==3 && session.getStats().getStageReached()==3,
                "waves add up over the run and the latest stage is reached");
        second.getGameplayEvents().publish(new event.ShotFiredEvent("waves","late-screen-shot",2));
        first.getGameplayEvents().publish(new event.ShotFiredEvent("waves","earlier-screen-shot",1));
        check(session.getStats().getShotsFired()==3, "every screen of the run reports to its session");
        GameScreen custom = GameScreen.forCustomLevel(new GameState(1,0,3,0,0),
                new level.LevelDefinition(1, "custom-records", "Custom", Arrays.asList(
                        new level.WaveDefinition("only", new level.EnemyCell[][] {{new level.EnemyCell("C", 1)}},
                                80, 120, 90, 3000))), 1, 448, 520, 60);
        custom.initialize();
        check(custom.getActiveRunId()==null, "custom levels have no run session to record");
    }
    @SuppressWarnings("unchecked")
    private static void infinite(RecordsService service) throws Exception {
        service.startRun("infinite");
        RunSession session = new RunSession("infinite", RunMode.INFINITE);
        InfiniteGameScreen game = new InfiniteGameScreen(new GameState(1,0,3,0,0),448,520,60,session);
        game.initialize();
        List<ZigzagEnemyShip> enemies = (List<ZigzagEnemyShip>)get(game,"enemies");
        Set<Bullet> bullets = (Set<Bullet>)get(game,"bullets");
        final List<String> events = new ArrayList<String>();
        game.getGameplayEvents().subscribe(event.EnemyDefeatedEvent.class,
                new event.GameplayEventListener<event.EnemyDefeatedEvent>() {
                    @Override
                    public void onEvent(final event.EnemyDefeatedEvent e) { events.add(e.getEventId()); }
                });
        enemies.add(new ZigzagEnemyShip(100,100,DrawManager.SpriteType.EnemyShipA1,1));
        bullets.add(BulletPool.getBullet(104,104,-4));
        call(game,"manageCollisions"); call(game,"manageCollisions");
        check(events.size()==1 && game.getGameState().getShipsDestroyed()==1,"infinite bullet kill is counted once");
        enemies.add(new ZigzagEnemyShip(100,600,DrawManager.SpriteType.EnemyShipA1,1));
        call(game,"moveEnemies");
        check(events.size()==1,"escaped infinite enemy does not count as defeated");
        Ship player=(Ship)get(game,"ship");
        enemies.add(new ZigzagEnemyShip(player.getPositionX(),player.getPositionY(),DrawManager.SpriteType.EnemyShipA1,1));
        call(game,"manageCollisions");
        check(events.size()==1 && game.getGameState().getLivesRemaining()==2,"player contact loses life without awarding a defeat");
        set(game,"gameOver",true);
        enemies.add(new ZigzagEnemyShip(100,100,DrawManager.SpriteType.EnemyShipA1,1));
        bullets.add(BulletPool.getBullet(104,104,-4)); call(game,"manageCollisions");
        check(events.size()==1,"infinite end animation cannot create more defeats");
        RunStats stats = session.getStats();
        check(stats.getHits()==1 && stats.getTimesHit()==1, "infinite session counts its defeat and the contact hit");
        check(stats.getStageReached()==0 && stats.getWavesReached()==1, "infinite starts its first wave without a campaign stage");
    }
}
