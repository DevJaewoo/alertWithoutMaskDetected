# import the necessary packages
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input
from tensorflow.keras.preprocessing.image import img_to_array
from tensorflow.keras.models import load_model
from imutils.video import VideoStream
import numpy as np
import argparse
import imutils
import time
import cv2
import glob
import os

import io
import threading
from datetime import datetime
from PIL import Image
from bt_module import Bluetooth

def detect_and_predict_mask(frame, faceNet, maskNet):

	(h, w) = frame.shape[:2]
	blob = cv2.dnn.blobFromImage(frame, 1.0, (300, 300),
		(104.0, 177.0, 123.0))

	faceNet.setInput(blob)
	detections = faceNet.forward()

	faces = []
	locs = []
	preds = []

	for i in range(0, detections.shape[2]):
		confidence = detections[0, 0, i, 2]

		if confidence > args["confidence"]:
			box = detections[0, 0, i, 3:7] * np.array([w, h, w, h])
			(startX, startY, endX, endY) = box.astype("int")

			(startX, startY) = (max(0, startX), max(0, startY))
			(endX, endY) = (min(w - 1, endX), min(h - 1, endY))

			face = frame[startY:endY, startX:endX]
			face = cv2.cvtColor(face, cv2.COLOR_BGR2RGB)
			face = cv2.resize(face, (224, 224))
			face = img_to_array(face)
			face = preprocess_input(face)

			faces.append(face)
			locs.append((startX, startY, endX, endY))

	if len(faces) > 0:
		faces = np.array(faces, dtype="float32")
		preds = maskNet.predict(faces, batch_size=32)

	return (locs, preds)

def get_without_mask_image(frame, locs, preds):

	result = False

	for (box, pred) in zip(locs, preds):
		(startX, startY, endX, endY) = box
		(mask, withoutMask) = pred

		if mask < withoutMask:
			color = (0, 0, 255)
			result = True
			cv2.rectangle(frame, (startX, startY), (endX, endY), color, 2)

	return (frame, result)

g_bluetooth = Bluetooth()
notifyUpdated = 0
notifyPath = ""

def send_image():
	if g_bluetooth.isConnected == False:
		return

	path = g_bluetooth.read(23).decode('utf-8')
	print("[INFO] Image path: " + path)

	byteImgIO = io.BytesIO()
	byteImg = Image.open(f"/home/pi/face_mask_detection/detected/" + path)
	byteImg.save(byteImgIO, "PNG")
	byteImgIO.seek(0)
	byteImg = byteImgIO.read()

	total_length = len(byteImg)
	fragmentNum = (total_length - 1) / 2048 + 1

	print(f"Image size: {total_length}", type(total_length))

	while True:
		data = bytes([(total_length >> i) & 0xFF for i in (24, 16, 8, 0)])
		g_bluetooth.write(data, 4)

		result = g_bluetooth.read(1)[0]

		if result == 0xFF:
			break

	while True:
		fragment = g_bluetooth.read(1)[0]
		print(f"fragment: {fragment}")

		if fragment == 0xFF:
			break

		send_data = byteImg[fragment * 2048 : (fragment + 1) * 2048]

		checksum = 0

		for i in send_data:
			checksum ^= i

		print(f"Checksum: {checksum}")

		g_bluetooth.write(send_data, len(send_data))
		g_bluetooth.write(bytes([checksum]), 1)

def send_image_list():
	if g_bluetooth.isConnected == False:
		return

	imgList = [x for x in os.listdir('/home/pi/face_mask_detection/detected') if x.endswith(".png")]
	print(imgList)

	s = ""
	for img in imgList:
		s += img + '\n'

	length = bytes([len(imgList)])

	g_bluetooth.write(length, len(length))
	g_bluetooth.write(s.encode('utf-8'), len(s))


def send_notify():
	if g_bluetooth.isConnected == False:
		return

	global notifyUpdated
	global notifyPath

	print(f"send notify updated: {notifyUpdated}, path: {notifyPath}")
	g_bluetooth.write(bytes([notifyUpdated]), 1)

	if notifyUpdated == 1:
		g_bluetooth.write(notifyPath.encode('utf-8'), len(notifyPath))

	notifyUpdated = 0
#	while True:
#		if notify_available == True:
#			break

#	g_bluetooth.write(path.encode('utf-8'), 24)


command_dict = {49: send_image, 50: send_image_list, 51: send_notify}

def run_bluetooth_service():

	while True:
		try:
			print("[INFO] Bluetooth: " + ("connected" if g_bluetooth.isConnected == True else "disconnected"))

			if g_bluetooth.isConnected == False:
				g_bluetooth.connect()

			print("[INFO] waiting for bluetooth command...")
			command = g_bluetooth.read(1)[0]

			if command == None:
				time.sleep(0.1)
				continue

			print("[INFO] Command read. value: {0}".format(command))

			notify_available = False
			command_dict[command]()
			notify_available = True

		except Exception as e:
			print(e)
			g_bluetooth.disconnect()


def remove_old_img():
	imgList = glob.glob("/home/pi/face_mask_detection/detected/*.png")

	if(len(imgList) >= 30):
		os.remove(imgList[0])


ap = argparse.ArgumentParser()
ap.add_argument("-f", "--face", type=str,
	default="/home/pi/face_mask_detection/face_detector",
	help="path to face detector model directory")
ap.add_argument("-m", "--model", type=str,
	default="/home/pi/face_mask_detection/mask_detector.model",
	help="path to trained face mask detector model")
ap.add_argument("-c", "--confidence", type=float, default=0.5,
	help="minimum probability to filter weak detections")
args = vars(ap.parse_args())

print("[INFO] loading face detector model...")
prototxtPath = os.path.sep.join([args["face"], "deploy.prototxt"])
weightsPath = os.path.sep.join([args["face"],
	"res10_300x300_ssd_iter_140000.caffemodel"])
faceNet = cv2.dnn.readNet(prototxtPath, weightsPath)

print("[INFO] loading face mask detector model...")
maskNet = load_model(args["model"])

print("[INFO] starting video stream...")
vs = VideoStream(usePiCamera=True).start()
time.sleep(1.0)

t = threading.Thread(target=run_bluetooth_service)
t.start()

currentTime = datetime.now()
while True:
	frame = vs.read()
	frame = imutils.resize(frame, width=500)

	(locs, preds) = detect_and_predict_mask(frame, faceNet, maskNet)
	(frame, result) = get_without_mask_image(frame, locs, preds)

	if result == True and (datetime.now() - currentTime).seconds >= 10:
		currentTime = datetime.now()
		str = currentTime.strftime("%Y-%m-%d_%H-%M-%S") + ".png"
		cv2.imwrite("/home/pi/face_mask_detection/detected/" + str, frame)
		#send_notify(str)
		notifyPath = str
		notifyUpdated = 1

	#cv2.imshow("mask", frame)
	cv2.waitKey(1)

cv2.destroyAllWindows()
vs.stop()
