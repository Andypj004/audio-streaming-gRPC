package com.audio.audio_streaming;

import audio_service.AudioService;
import com.audio.audio_streaming.controller.AudioClient;
import io.grpc.StatusRuntimeException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class JavaApplication {

	public static void main(String[] args) {
		//SpringApplication.run(JavaApplication.class, args);

		AudioClient client = new AudioClient("localhost", 50051);

		try{
			// List available MP3 files
			System.out.println("Listing available MP3 files...");
			List<String> availableFiles = client.listAvailableMP3();
			if (availableFiles.isEmpty()) {
				System.out.println("No MP3 files available.");
				return;
			}

			System.out.println("Available MP3 files:");
			for (int i = 0; i < availableFiles.size(); i++) {
				System.out.println((i + 1) + ". " + availableFiles.get(i));
			}

			// Ask the user to select a file to play
			System.out.print("\nSelect a file to play (number): ");
			int selection = Integer.parseInt(System.console().readLine());
			if (selection < 1 || selection > availableFiles.size()) {
				System.out.println("Invalid selection.");
				return;
			}

			String selectedFile = availableFiles.get(selection - 1);

			// Get metadata for the selected file
			AudioService.Metadata metadata = client.getMetadata(selectedFile);
			System.out.println("\n--- File Metadata ---");
			System.out.println("File Name         : " + metadata.getFileName());
			System.out.println("Duration          : " + metadata.getDurationSeconds() + " seconds");
			System.out.println("Sample Rate (Hz)  : " + metadata.getSampleRate());
			System.out.println("Channels          : " + metadata.getChannels());
			System.out.println("Codec             : " + metadata.getCodec());

			// Play the selected audio
			client.playAudio(selectedFile);


		} catch (InterruptedException | StatusRuntimeException e) {
			e.printStackTrace();
		} finally {
			client.channel.shutdownNow();
		}

	}

}
