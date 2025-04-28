import grpc
import audio_service_pb2 as pb2
import audio_service_pb2_grpc as pb2_grpc
import pyaudio
import time
from termcolor import colored
from tqdm import tqdm

p = pyaudio.PyAudio()
CHUNK_SIZE = 4096

# Connect to the gRPC server and create stubs for audio and mp3 services
def connect_to_server():
    channel = grpc.insecure_channel('localhost:50051')
    audio_stub = pb2_grpc.AudioStreamServiceStub(channel)
    mp3_stub = pb2_grpc.MP3ServiceStub(channel)
    return audio_stub, mp3_stub

# List available MP3 files on the server
def list_available_mp3(mp3_stub):
    print(colored("Listing available MP3 files...\n", 'green'))
    request = pb2.Empty()
    response = mp3_stub.ListAvailableMP3(request)
    return response.file_names

# Play the selected audio file
def play_audio(audio_stub, mp3_stub, file_name):
    print(colored(f"Playing: {file_name}\n", 'green'))

    # Request metadata for the selected file
    metadata_request = pb2.MetadataRequest(file_name=file_name)
    metadata = audio_stub.GetMetadata(metadata_request)

    if not metadata:
        print(colored("Could not fetch metadata.", 'red'))
        return

    # Open PyAudio stream based on metadata
    stream = p.open(
        format=pyaudio.paInt16,      # 16-bit audio format
        channels=metadata.channels,  # Number of audio channels
        rate=metadata.sample_rate,   # Sampling rate (Hz)
        output=True,
        frames_per_buffer=CHUNK_SIZE
    )

    # Request to stream audio
    request = pb2.StreamRequest(file_name=file_name)
    audio_stream = audio_stub.StreamAudio(request)

    start_time = time.time()
    total_duration = metadata.duration_seconds

    # Display a progress bar for playback
    progress_bar = tqdm(total=total_duration, desc="Playback (seconds)", unit="s")

    # Receive audio chunks and write them to the audio output
    for audio_chunk in audio_stream:
        stream.write(audio_chunk.chunk_data)

        elapsed_time = time.time() - start_time
        progress_bar.n = min(int(elapsed_time), total_duration)
        progress_bar.update(0)

    progress_bar.close()

    print(colored("\nPlayback finished.", 'green'))
    stream.stop_stream()
    stream.close()

# Main client program
def main():
    audio_stub, mp3_stub = connect_to_server()
    available_files = list_available_mp3(mp3_stub)

    if not available_files:
        print(colored("No MP3 files available.", 'red'))
        return

    print("\n--- AUDIO STREAMING CLIENT ---")
    print("Available MP3 files:\n")
    for idx, f in enumerate(available_files, 1):
        print(f"{idx}. {f}")
    print("0. Exit") 
    print("-------------------------------")
    
    while True:
        try:
            # Ask the user to select a file by number
            selection = int(input("\nSelect a file to play (number): "))
            if selection == 0:
                print(colored("Exiting program. Goodbye!", 'cyan'))
                break  # Exit the loop and end program
            elif 1 <= selection <= len(available_files):
                selected_file = available_files[selection - 1]

                # --- Show metadata before playing ---
                metadata = audio_stub.GetMetadata(pb2.MetadataRequest(file_name=selected_file))

                print("\n--- FILE METADATA ---")
                print(f"File Name         : {metadata.file_name}")
                minutes, seconds = divmod(metadata.duration_seconds, 60)
                print(f"Duration          : {minutes} min {seconds} sec")
                print(f"Sample Rate (Hz)  : {metadata.sample_rate}")
                print(f"Channels          : {metadata.channels}")
                print(f"Codec             : {metadata.codec}")
                print("-------------------------------\n")

                # Now play the selected file
                play_audio(audio_stub, mp3_stub, selected_file)
            else:
                print(colored("Invalid selection. Please try again.", 'red'))
        except ValueError:
            print(colored("Invalid input. Please enter a number.", 'red'))

# Entry point
if __name__ == '__main__':
    main()
