package com.audio.audio_streaming;

import audio_service.AudioService;
import com.audio.audio_streaming.controller.AudioClient;
import io.grpc.StatusRuntimeException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class JavaApplication {
	public static void main(String[] args) {
		// Create scanner for user input
		Scanner scanner = new Scanner(System.in);
		System.out.println("=== AUDIO STREAMING CLIENT ===");
		System.out.print("Enter server address (default: localhost): ");
		String host = scanner.nextLine().trim();
		if (host.isEmpty()) {
			host = "localhost";
		}
		System.out.print("Enter server port (default: 50051): ");
		String portStr = scanner.nextLine().trim();
		int port = portStr.isEmpty() ? 50051 : Integer.parseInt(portStr);

		// Create audio client
		AudioClient client = new AudioClient(host, port);

		try {
			while (true) {
				// List available MP3 files
				System.out.println("\nListing available MP3 files...");
				List<String> availableFiles = client.listAvailableMP3();

				if (availableFiles.isEmpty()) {
					System.out.println("No MP3 files available.");
					break;
				}

				System.out.println("\n=== AVAILABLE MP3 FILES ===");
				for (int i = 0; i < availableFiles.size(); i++) {
					System.out.println((i + 1) + ". " + availableFiles.get(i));
				}
				System.out.println("0. Exit");
				System.out.println("===========================");

				// Ask the user to select a file to play
				System.out.print("\nSelect a file to play (number): ");
				int selection;
				try {
					selection = Integer.parseInt(scanner.nextLine().trim());
				} catch (NumberFormatException e) {
					System.out.println("Invalid input. Please enter a number.");
					continue;
				}

				// Exit if user selects 0
				if (selection == 0) {
					System.out.println("Exiting program. Goodbye!");
					break;
				}

				if (selection < 1 || selection > availableFiles.size()) {
					System.out.println("Invalid selection. Please try again.");
					continue;
				}

				String selectedFile = availableFiles.get(selection - 1);

				// Get metadata for the selected file
				AudioService.Metadata metadata = client.getMetadata(selectedFile);
				System.out.println("\n=== FILE METADATA ===");
				System.out.println("File Name         : " + metadata.getFileName());
				int minutes = metadata.getDurationSeconds() / 60;
				int seconds = metadata.getDurationSeconds() % 60;
				System.out.println("Duration          : " + minutes + " min " + seconds + " sec");
				System.out.println("Sample Rate (Hz)  : " + metadata.getSampleRate());
				System.out.println("Channels          : " + metadata.getChannels());
				System.out.println("Codec             : " + metadata.getCodec());
				System.out.println("=====================");

				// Play the selected audio with pause/resume functionality
				System.out.println("\nStarting playback...");
				System.out.println("Controles: 'p' para pausar/continuar, 'q' para detener");
				client.playAudio(selectedFile);

				// Ask if user wants to play another file
				System.out.print("\nPlay another file? (y/n): ");
				String answer = scanner.nextLine().trim().toLowerCase();
				if (!answer.startsWith("y")) {
					System.out.println("Exiting program. Goodbye!");
					break;
				}
			}
		} catch (InterruptedException | StatusRuntimeException e) {
			System.err.println("Error occurred: " + e.getMessage());
			e.printStackTrace();
		} finally {
			// Cleanup
			scanner.close();
			client.channel.shutdownNow();
			try {
				client.channel.awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}