from bluetooth import *
import threading
import os

class Bluetooth():

	def __init__(self):
		self.uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"
		self.server_sock = None
		self.client_sock = None
		self.isConnected = False

		self.sessionLock = threading.Lock()
		self.readLock = threading.Lock()
		self.writeLock = threading.Lock()

		try:
			os.system('sudo hciconfig hci0 piscan')
			os.system('sudo chmod 777 /run/sdp')
		except Exception as e:
			print(e)

	def connect(self):

		with self.sessionLock:
			print("Connect bluetooth")

			if self.isConnected == True:
				print('Already connected')
				return

			self.server_sock=BluetoothSocket( RFCOMM )
			self.server_sock.bind(('',PORT_ANY))
			self.server_sock.listen(1)

			port = self.server_sock.getsockname()[1]

			# 블루투스 서비스를 Advertise
			advertise_service(self.server_sock, "MaskDetection",
				service_id = self.uuid,
				service_classes = [self.uuid, SERIAL_PORT_CLASS ],
				profiles = [ SERIAL_PORT_PROFILE ] )

			print("Waiting for connection : channel %d" % port)
			# 클라이언트가 연결될 때까지 대기
			self.client_sock, client_info = self.server_sock.accept()
#			self.client_sock.settimeout(1)
			print('Accepted connection from ', client_info)

			self.isConnected = True

	def disconnect(self):

		with self.sessionLock:
			print("Disconnect bluetooth")

			if self.isConnected == False:
				print('Already disconnected')
				return

			if self.server_sock != None:
				self.server_sock.close()
				self.server_sock = None

			if self.client_sock != None:
				self.client_sock.close()
				self.client_sock = None

			self.isConnected = False
			return

	def reconnect(self):
		self.disconnect()
		self.connect()

	def read(self, length):

		with self.readLock:
			if self.isConnected == False:
				print('Not connected')
				return

			try:
				retVal = bytes()

				while len(retVal) < length:
					retVal += self.client_sock.recv(1024)

				return retVal[:length]

			except Exception as err:
				print('Read failed. Error: ', err)
				return None

	def write(self, data, length):

		with self.writeLock:
			if self.isConnected == False:
				print('Not connected')
				return

			try:
				size = 0

				while size < length:
					size += self.client_sock.send(data[size:])

			except Exception as err:
				print('Write failed. Error: ', err)
				return

if __name__ == '__main__':
	bt = Bluetooth()
	bt.connect()
	data = bt.read(5)
	bt.write(data, 5)
	bt.disconnect()
