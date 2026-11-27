(function () {
  var ticker = Java.loadClass('com.google.common.base.Ticker').systemTicker();
  var warmupNanos = 120000000000;
  var sampleNanos = 300000000000;
  var active = null;

  function now() {
    return Number(ticker.read());
  }

  function register(event, name, warmup, sample) {
    event.register(event.commands.literal(name)
      .requires(function (source) { return source.hasPermission(4); })
      .executes(function () {
        if (active !== null) return 0;
        active = {start: now(), warmup: warmup, sample: sample, rows: []};
        console.info('PERF_START ' + active.start);
        return 1;
      }));
  }

  ServerEvents.commandRegistry(function (event) {
    register(event, 'perfticks', warmupNanos, sampleNanos);
    register(event, 'perfticksprobe', 2000000000, 5000000000);
  });

  ServerEvents.tick(function (event) {
    if (active === null) return;
    var offset = now() - active.start - active.warmup;
    if (offset < 0) return;
    if (offset < active.sample) {
      var completedTick = event.server.getTickCount() - 1;
      var ring = event.server.getTickTimesNanos();
      if (completedTick > 0) {
        active.rows.push([completedTick, offset, Number(ring[completedTick % ring.length])]);
      }
      return;
    }

    var rows = active.rows;
    var window = {
      warmupStartNs: active.start,
      sampleStartNs: active.start + active.warmup,
      sampleEndNs: active.start + active.warmup + active.sample,
      observedEndOffsetNs: offset,
      firstTick: rows.length ? rows[0][0] : null,
      lastTick: rows.length ? rows[rows.length - 1][0] : null,
      count: rows.length
    };
    active = null;
    for (var index = 0; index < rows.length; index += 100) {
      console.info('PERF_TICKS ' + JSON.stringify(rows.slice(index, index + 100)));
    }
    console.info('PERF_WINDOW ' + JSON.stringify(window));
  });
})();
