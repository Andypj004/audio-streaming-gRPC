package com.audio.audio_streaming.controller;

import audio_service.AudioService;
import audio_service.AudioStreamServiceGrpc;
import audio_service.MP3ServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioClient {

    public ManagedChannel channel;
    private MP3ServiceGrpc.MP3ServiceBlockingStub mp3Stub;
    private AudioStreamServiceGrpc.AudioStreamServiceBlockingStub audioStreamStub;
    private AudioStreamServiceGrpc.AudioStreamServiceStub asyncAudioStreamStub;
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicBoolean isPlaying = new AtomicBoolean(false);

    public AudioClient(String host, int port){
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext().build();
        this.mp3Stub = MP3ServiceGrpc.newBlockingStub(channel);
        this.audioStreamStub = AudioStreamServiceGrpc.newBlockingStub(channel);
        this.asyncAudioStreamStub = AudioStreamServiceGrpc.newStub(channel);
    }

    public List<String> listAvailableMP3(){
        AudioService.MP3List response = mp3Stub.listAvailableMP3(AudioService.Empty.newBuilder().build());
        return response.getFileNamesList();
    }

    public AudioService.Metadata getMetadata(String filename){
        AudioService.MetadataRequest request = AudioService.MetadataRequest.newBuilder().setFileName(filename).build();
        return audioStreamStub.getMetadata(request);
    }

    public void playAudio(String filename) throws InterruptedException {
        AudioService.Metadata metadata = getMetadata(filename);

        // Estimar tamaño total en bytes
        int bytesPerSample = 2; // 16 bits = 2 bytes
        int frameSize = metadata.getChannels() * bytesPerSample;
        long estimatedTotalBytes = (long) metadata.getDurationSeconds() * metadata.getSampleRate() * frameSize;

        AudioService.StreamRequest request = AudioService.StreamRequest.newBuilder().setFileName(filename).build();
        final CountDownLatch finishLatch = new CountDownLatch(1);

        // Reiniciar estado de reproducción
        isPaused.set(false);
        isPlaying.set(true);

        // Iniciar un hilo para escuchar comandos durante la reproducción
        startControlListener(finishLatch);

        StreamObserver<AudioService.AudioChunk> responseObserver = new StreamObserver<AudioService.AudioChunk>() {
            private AudioInputStream audioInputStream;
            private SourceDataLine sourceDataLine;
            private long totalBytesReceived = 0;

            @Override
            public void onNext(AudioService.AudioChunk audioChunk) {
                byte[] audioData = audioChunk.getChunkData().toByteArray();
                totalBytesReceived += audioData.length;

                if (audioInputStream == null){
                    try {
                        AudioFormat audioFormat = new AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED,
                                metadata.getSampleRate(),
                                16,
                                metadata.getChannels(),
                                metadata.getChannels() * 2,
                                metadata.getSampleRate(),
                                false
                        );

                        audioInputStream = new AudioInputStream(
                                new ByteArrayInputStream(audioData),
                                audioFormat,
                                audioData.length
                        );

                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
                        sourceDataLine.open(audioFormat);
                        sourceDataLine.start();
                    } catch (LineUnavailableException e) {
                        e.printStackTrace();
                    }
                }

                if (sourceDataLine != null) {
                    // Controlar la pausa mientras reproducimos
                    while (isPaused.get() && isPlaying.get()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    // Si ya no estamos reproduciendo (usuario presionó 'q'), salir
                    if (!isPlaying.get()) {
                        return;
                    }

                    sourceDataLine.write(audioData, 0, audioData.length);
                }

                // Mostrar barra de progreso
                int progress = (int)((totalBytesReceived * 100) / estimatedTotalBytes);
                String progressBar = "=".repeat(progress / 2) + " ".repeat(50 - (progress / 2));
                String status = isPaused.get() ? "PAUSADO" : "Reproduciendo";
                System.out.print("\r" + status + ": [" + progressBar + "] " + progress + "% (p: pausa/continúa, q: detener)");
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
                isPlaying.set(false);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("\nAudio streaming completed.");
                if (sourceDataLine != null) {
                    sourceDataLine.drain();
                    sourceDataLine.close();
                }
                isPlaying.set(false);
                finishLatch.countDown();
            }
        };

        asyncAudioStreamStub.streamAudio(request, responseObserver);

        boolean completed = finishLatch.await(5, TimeUnit.MINUTES);
        if (!completed) {
            System.out.println("Audio streaming timed out.");
        }
    }

    private void startControlListener(CountDownLatch finishLatch) {
        new Thread(() -> {
            Scanner controlScanner = new Scanner(System.in);
            System.out.println("Controles: 'p' para pausar/continuar, 'q' para detener");

            while (isPlaying.get()) {
                try {
                    // Verificar si hay entrada disponible para no bloquear el hilo
                    if (System.in.available() > 0) {
                        String command = controlScanner.nextLine().trim().toLowerCase();

                        if (command.equals("p")) {
                            // Cambiar estado de pausa
                            boolean newPauseState = !isPaused.get();
                            isPaused.set(newPauseState);
                            System.out.print("\r" + (newPauseState ? "PAUSADO" : "CONTINUANDO") + "                                         ");
                        } else if (command.equals("q")) {
                            // Detener reproducción
                            System.out.println("\nDeteniendo reproducción...");
                            isPlaying.set(false);
                            finishLatch.countDown();
                            break;
                        }
                    }

                    Thread.sleep(100);
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }
}