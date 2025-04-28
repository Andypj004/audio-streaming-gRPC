import os
import grpc
from concurrent import futures
from pydub import AudioSegment
import audio_service_pb2 as pb2
import audio_service_pb2_grpc as pb2_grpc
import io

AUDIO_DIRECTORY = './audio_files/'

# Server class to list available MP3 files
class MP3Service(pb2_grpc.MP3ServiceServicer):
    def ListAvailableMP3(self, request, context):
        # List all .mp3 files in the audio directory
        files = [f for f in os.listdir(AUDIO_DIRECTORY) if f.endswith('.mp3')]
        return pb2.MP3List(file_names=files)

# Server class to stream audio and provide metadata
class AudioStreamService(pb2_grpc.AudioStreamServiceServicer):
    def StreamAudio(self, request, context):
        # Build full path to requested MP3 file
        file_name = request.file_name
        mp3_path = os.path.join(AUDIO_DIRECTORY, file_name)

        # Handle file not found error
        if not os.path.exists(mp3_path):
            context.set_details(f'File {file_name} not found.')
            context.set_code(grpc.StatusCode.NOT_FOUND)
            return

        # Convert MP3 to raw PCM audio data in memory
        audio = AudioSegment.from_mp3(mp3_path)
        pcm_data = audio.raw_data

        # Stream the PCM audio data in chunks to the client
        chunk_size = 4096
        sequence_number = 0
        for i in range(0, len(pcm_data), chunk_size):
            chunk = pcm_data[i:i+chunk_size]
            yield pb2.AudioChunk(
                chunk_data=chunk,
                sequence_number=sequence_number
            )
            sequence_number += 1

    def GetMetadata(self, request, context):
        # Build full path to requested MP3 file
        file_name = request.file_name
        mp3_path = os.path.join(AUDIO_DIRECTORY, file_name)

        # Handle file not found error
        if not os.path.exists(mp3_path):
            context.set_details(f'File {file_name} not found.')
            context.set_code(grpc.StatusCode.NOT_FOUND)
            return pb2.Metadata()

        # Load the MP3 file to extract metadata
        audio = AudioSegment.from_mp3(mp3_path)
        return pb2.Metadata(
            file_name=file_name,
            duration_seconds=len(audio) // 1000,  # Duration in seconds
            sample_rate=audio.frame_rate,          # Sampling rate (Hz)
            channels=audio.channels,               # Number of audio channels
            codec="PCM"                            # Audio codec used
        )

# Function to start and run the gRPC server
def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    pb2_grpc.add_MP3ServiceServicer_to_server(MP3Service(), server)
    pb2_grpc.add_AudioStreamServiceServicer_to_server(AudioStreamService(), server)

    # Listen on all network interfaces at port 50051
    server.add_insecure_port('[::]:50051')
    print("gRPC server listening on port 50051...")
    server.start()
    server.wait_for_termination()

# Entry point to run the server
if __name__ == '__main__':
    serve()
