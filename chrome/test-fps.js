// A small client-side JS mod that attempts to keep perceived FPS up by optionally skipping frames
(function(){
  console.log('[fps-mod] loaded');
  var target = 20;
  var last = performance.now();
  var frames = 0;
  var fps = 0;
  var skip = 1; // render every frame initially
  function tick(now){
    frames++;
    if (now - last >= 1000){ fps = frames*1000/(now-last); frames=0; last=now; }
    // simple adaptive: if fps < target reduce rendering frequency
    if (fps > 0){
      if (fps < target) { skip = Math.min(4, skip+1); }
      else if (fps > target+5) { skip = Math.max(1, skip-1); }
    }
    // monkey-patch: store decision for renderers to check
    window.__eaglerhub_fps_skip = skip;
    window.requestAnimationFrame(function(){
      // nothing here; actual client will continue
    });
  }
  // start lightweight sampler
  function loop(){ window.requestAnimationFrame(function(now){ tick(now); setTimeout(loop, 200); }); }
  loop();
  // expose API
  window.EaglerHubMods = window.EaglerHubMods || {};
  window.EaglerHubMods.getFps = function(){ return fps; };
  window.EaglerHubMods.getSkip = function(){ return skip; };
})();
