package com.example.mixin.client;

import com.example.TemplateModClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticlesMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final
    private MinecraftClient client;

    // perfomance trackers
    private long lastPerformanceCheck= 0;
    private long lastFrameTime=System.nanoTime();
    private float averageFps= 60.0f;
    private int frameCounter=0;

    //adaptive stuff
    private boolean adaptiveMode =false;
    private int originalRenderDistance= -1;
    private ParticlesMode originalParticlesMode =null;
    private boolean originalBobView= true;
    private boolean performanceOptimizationsActive=false;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(CallbackInfo ci) {
        long currentTime= System.currentTimeMillis();

        // check perfomance every 1 second
        if(currentTime-lastPerformanceCheck>1000) {
            updatePerformanceMetrics();
            checkAndApplyAdaptiveOptimizations( );
            lastPerformanceCheck=currentTime;
        }
    }

    @Inject(method="render",at=@At("TAIL"))
    private void onRenderEnd(CallbackInfo ci){
        updateFrameMetrics( );
    }

    // modify fov dynamicaly
    @ModifyVariable(method = "getFov", at = @At("HEAD"), argsOnly = true)
    private double modifyFov(double fov){
        if(!TemplateModClient.config.optimizeRendering||!adaptiveMode) {
            return fov;
        }

        // reduce fov when fps is bad
        if(averageFps<30&&TemplateModClient.config.shouldReduceLag()) {
            return Math.max(fov*0.95,70.0);
        }

        if(averageFps<45){
            return Math.max(fov*0.98, 85.0);
        }

        return fov;
    }

    // calculate runing average fps
    private void updateFrameMetrics( ) {
        long currentFrameTime=System.nanoTime();
        long frameTimeDelta=currentFrameTime-lastFrameTime;
        lastFrameTime =currentFrameTime;

        if(frameTimeDelta>0){
            float currentFps= 1_000_000_000.0f/frameTimeDelta;
            // smooth fps calculation
            averageFps=(averageFps*0.9f)+(currentFps*0.1f);
        }

        frameCounter++ ;
    }

    // monitor sistem perfomance
    private void updatePerformanceMetrics(){
        Runtime runtime=Runtime.getRuntime();
        long totalMemory=runtime.totalMemory( );
        long freeMemory=runtime.freeMemory();
        long usedMemory=totalMemory-freeMemory;
        double memoryUsagePercent=(double)usedMemory/runtime.maxMemory();

        // enable adaptive mode when perfomance is poor
        adaptiveMode=(averageFps<50||memoryUsagePercent>0.8)&&TemplateModClient.config.shouldReduceLag();
    }

    // main adaptive optimizaton logic
    private void checkAndApplyAdaptiveOptimizations( ){
        if(!TemplateModClient.config.optimizeRendering){
            restoreOriginalSettings( );
            return;
        }

        // determin if we need to optimize
        boolean shouldOptimize=averageFps<40||(Runtime.getRuntime().freeMemory()<Runtime.getRuntime().totalMemory()*0.15);

        if(shouldOptimize&&!performanceOptimizationsActive){
            applyPerformanceOptimizations( );
        }else if(!shouldOptimize&&performanceOptimizationsActive) {
            restoreOriginalSettings();
        }
    }

    // apply agresive perfomance optimizatons
    private void applyPerformanceOptimizations(){
        if(client.options==null)return;

        performanceOptimizationsActive= true;

        // store original setings for restoration later
        if(originalRenderDistance==-1){
            originalRenderDistance=client.options.getViewDistance().getValue();
        }
        if(originalParticlesMode==null) {
            originalParticlesMode=client.options.getParticles().getValue();
        }
        originalBobView=client.options.getBobView().getValue( );

        int currentDistance= client.options.getViewDistance().getValue();
        int targetDistance;

        // agresive render distance reduction based on fps
        if(averageFps<20){
            targetDistance=Math.max(4,currentDistance-4); // emergency mode
        }else if(averageFps<35) {
            targetDistance=Math.max(6,currentDistance-2); // moderate reduction
        }else{
            targetDistance=Math.max(8,currentDistance-1); // light reduction
        }

        if(targetDistance!=currentDistance){
            client.options.getViewDistance().setValue(targetDistance);
        }

        // limit max fps when strugling
        if(client.options.getMaxFps().getValue()>60&&averageFps<30){
            client.options.getMaxFps().setValue(60);
        }

        // disable expensiv visual efects when fps is very low
        if(averageFps<25) {
            if(client.options.getBobView().getValue()){
                client.options.getBobView().setValue(false);
            }

            // reduce particle count progresively
            ParticlesMode currentParticles=client.options.getParticles().getValue();
            if(currentParticles==ParticlesMode.ALL){
                client.options.getParticles().setValue(ParticlesMode.DECREASED);
            }else if(currentParticles==ParticlesMode.DECREASED&&averageFps<15){
                client.options.getParticles().setValue(ParticlesMode.MINIMAL);
            }
        }

        // emergency optimizatons for extremly poor perfomance
        if(averageFps<15){
            // disable ambient oclusion
            if(client.options.getAo().getValue( )){
                client.options.getAo().setValue(false);
            }

            // reduce simulaton distance
            int simDistance=client.options.getSimulationDistance().getValue();
            if(simDistance>5) {
                client.options.getSimulationDistance().setValue(Math.max(5,simDistance-2));
            }
        }
    }

    // gradualy restore original setings when perfomance improves
    private void restoreOriginalSettings(){
        if(!performanceOptimizationsActive||client.options==null)return;

        performanceOptimizationsActive=false;

        // gradualy increase render distance back to original
        if(originalRenderDistance!=-1){
            int currentDistance=client.options.getViewDistance().getValue();

            if(averageFps>55&&currentDistance<originalRenderDistance) {
                int newDistance=Math.min(originalRenderDistance,currentDistance+1);
                client.options.getViewDistance().setValue(newDistance);
            }
        }

        // restore visual efects when perfomance is good
        if(averageFps>50){
            client.options.getBobView().setValue(originalBobView);

            if(originalParticlesMode!=null){
                client.options.getParticles().setValue(originalParticlesMode);
            }

            client.options.getAo().setValue(true);
        }

        // restore simulaton distance
        if(averageFps>45) {
            int currentSimDistance=client.options.getSimulationDistance().getValue();
            int targetSimDistance=Math.min(10,currentSimDistance+1);
            client.options.getSimulationDistance().setValue(targetSimDistance);
        }
    }

    // public geters for debuging and monitoring
    public float getAverageFps(){
        return averageFps;
    }

    public boolean isAdaptiveModeActive( ){
        return adaptiveMode;
    }

    public boolean arePerformanceOptimizationsActive(){
        return performanceOptimizationsActive;
    }
}