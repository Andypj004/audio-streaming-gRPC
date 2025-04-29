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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AudioClient {

    public ManagedChannel channel;
    private MP3ServiceGrpc.MP3ServiceBlockingStub mp3Stub;
    private AudioStreamServiceGrpc.AudioStreamServiceBlockingStub audioStreamStub;
    private AudioStreamServiceGrpc.AudioStreamServiceStub asyncAudioStreamStub;


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

    public void playAudio(String filename) throws InterruptedException{
        AudioService.StreamRequest request = AudioService.StreamRequest.newBuilder().setFileName(filename).build();
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<AudioService.AudioChunk> responseObserver = new StreamObserver<AudioService.AudioChunk>() {
            private AudioInputStream audioInputStream;
            private SourceDataLine sourceDataLine;
            @Override
            public void onNext(AudioService.AudioChunk audioChunk) {
                byte[] audioData = audioChunk.getChunkData().toByteArray();
                if (audioInputStream == null){
                    try {
                        // Create audio stream from the first chunk
                        AudioFormat audioFormat = new AudioFormat(
                                AudioFormat.Encoding.PCM_SIGNED,
                                44100, // Sample rate
                                16,    // Sample size (16-bit)
                                2,     // Channels (Stereo)
                                4,     // Frame size (2 channels * 2 bytes/sample)
                                44100, // Frame rate
                                false  // Big-endian
                        );

                        audioInputStream = new AudioInputStream(
                                new ByteArrayInputStream(audioData),
                                audioFormat,
                                audioData.length
                        );

                        // Open the line to play audio
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
                        sourceDataLine.open(audioFormat);
                        sourceDataLine.start();
                    }  catch (LineUnavailableException e) {
                        e.printStackTrace();
                    }
                }

                // Write the audio chunk to the output line
                if (sourceDataLine != null) {
                    sourceDataLine.write(audioData, 0, audioData.length);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Audio streaming completed.");
                if (sourceDataLine != null) {
                    sourceDataLine.drain(); // Ensure that any remaining data is played
                    sourceDataLine.close();
                }
                finishLatch.countDown();
            }
        };

        // Stream audio chunks
        asyncAudioStreamStub.streamAudio(request, responseObserver);

        // Wait for the streaming to finish (give enough time for all chunks to be received)
        boolean completed = finishLatch.await(5, TimeUnit.MINUTES);  // Increase the timeout to 5 minutes
        if (!completed) {
            System.out.println("Audio streaming timed out.");
        }
    }

}
