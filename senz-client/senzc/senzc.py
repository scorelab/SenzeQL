from twisted.internet.protocol import DatagramProtocol
from twisted.internet import reactor
import datetime

import socket
import time
import sys
import thread
import os.path


lib_path = os.path.abspath('../utils')
sys.path.append(lib_path)
from myParser import *
from myCrypto import *
import hashlib

lib_path1 = os.path.abspath('../')
sys.path.append(lib_path1)
from config import *

device = ""
serverPubKey = ""
aesKeys = {}

class mySensorDatagramProtocol(DatagramProtocol):
    def __init__(self, host, port, reactor):
        self.ip = socket.gethostbyname(host)
        self.port = port

    def readSenze(self):
        while True:
            response = raw_input("Enter your Senze:")
            self.sendDatagram(response)

    def startProtocol(self):
        self.transport.connect(self.ip, self.port)
        if state == 'INITIAL':
            #If system is at the initial state, it will send the device creation Senze
            self.register()
        else:
            thread.start_new_thread(self.readSenze, ())

    def stopProtocol(self):
        #on disconnect
        self._reactor.listenUDP(0, self)
        print "STOP **************"

    #Let's send a ping to keep open the active
    def sendPing(self, delay):
        global connections
        global database
        for recipient in connections:
            forward = connections[recipient]
            timeGap = time.time() - connectionsTime[recipient]
            #print timeGap
            #If there are no activities in an hour, let's close the connection
            if (timeGap < 3600):
                self.transport.write("PING", forward)
            else:
                connections[recipient] = 0
        reactor.callLater(delay, self.sendPing, delay=delay)

    def register(self):
        global server
        cry = myCrypto(name=device)
        senze = 'SHARE #pubkey %s @%s' % (pubkey, server)
        senze = cry.signSENZE(senze)
        self.transport.write(senze)

    def sendDatagram(self, senze):
        cry = myCrypto(name=device)
        senze = cry.signSENZE(senze)
        print senze
        self.transport.write(senze)

    #Senze response should be built as follows by calling the functions in the driver class
    def sendDataSenze(self, sensors, data, recipient):
        global device
        global aesKeys

        response = 'DATA'
        driver = myDriver()
        cry = myCrypto(device)

        for sensor in sensors:
            #If AES key is requested
            if "key" == sensor:
                aeskey = ""
                #Generate AES Key
                if cry.generateAES(driver.readTime()):
                    aeskey = cry.key
                    #Save AES key
                    aesKeys[recipient] = aeskey
                    #AES key is encrypted by the recipient public key
                    rep = myCrypto(recipient)
                    encKey = rep.encryptRSA(b64encode(aeskey))
                response = '%s #key %s' % (response, encKey)

            #If time is requested
            elif "time" == sensor:
                response = '%s #time %s' % (response, driver.readTime())
            else:
                response = '%s #%s NULL' % (response, sensor)

        response = "%s @%s" % (response, recipient)
        self.sendDatagram(senze=response)
    
    def handleServerResponse(self, senze):
        sender = senze.getSender()
        data = senze.getData()
        sensors = senze.getSensors()
        cmd = senze.getCmd()

        if cmd == "DATA":
            if 'msg' in sensors and 'UserRemoved' in data['msg']:
                cry = myCrypto(device)
                try:
                    os.remove(".devicename")
                    os.remove(cry.pubKeyLoc)
                    os.remove(cry.privKeyLoc)
                    print "Device was successfully removed"
                except OSError:
                    print "Cannot remove user configuration files"
                reactor.stop()

            elif 'pubkey' in sensors and data['pubkey'] != "" and 'name' in sensors and data['name'] != "":
                recipient = myCrypto(data['name'])
                if recipient.saveRSAPubKey(data['pubkey']):
                    print "Public key=> " + data['pubkey'] + " Saved."
                else:
                    print "Error: Saving the public key."

    def handleDeviceResponse(self, senze):
        global device
        global aesKeys
        sender = senze.getSender()
        data = senze.getData()
        sensors = senze.getSensors()
        cmd = senze.getCmd()
        if cmd == "DATA":
            for sensor in sensors:
                if sensor in data.keys():
                    print sensor + "=>" + data[sensor]

            #Received and saved the AES key
            if 'key' in sensors and data['key'] != "":
                #Key need to be decrypted by using the private key
                cry = myCrypto(device)
                dec = cry.decryptRSA(data['key'])
                aesKeys[sender] = b64decode(dec)

            #Decrypt and show the gps data
        elif cmd == "SHARE":
            print "This should be implemented"

        elif cmd == "UNSHAR":
            print "This should be implemented"

        elif cmd == "GET":
            #If GET Senze was received. The device must handle it.
            reactor.callLater(1,self.sendDataSenze, sensors=sensors, data=data, recipient=sender)


        elif cmd == "PUT":
            reactor.callLater(1, self.handlePUTSenze, sensors=sensors, data=data, recipient=sender)
        else:
            print "Unknown command"

    def datagramReceived(self, datagram, host):
        global device
        print 'Datagram received: ', repr(datagram)

        parser = myParser(datagram)
        recipients = parser.getUsers()
        sender = parser.getSender()
        signature = parser.getSignature()
        data = parser.getData()
        sensors = parser.getSensors()
        cmd = parser.getCmd()

        validQuery = False
        cry = myCrypto(device)
        if state == "READY":
            if serverPubkey != "" and sender == "mysensors":
                if cry.verifySENZE(parser, serverPubkey):
                    self.handleServerResponse(parser)
                else:
                    print "SENZE Verification failed"
            else:
                if sender != "":
                    recipient = myCrypto(sender)
                    if os.path.isfile(recipient.pubKeyLoc):
                        pub = recipient.loadRSAPubKey()
                    else:
                        pub = ""
                    if pub != "" and cry.verifySENZE(parser, pub):
                        print "SENZE Verified"
                        self.handleDeviceResponse(parser)
                    else:
                        print "SENZE Verification failed"

        else:
            if cmd == "DATA":
                if 'msg' in sensors and 'UserCreated' in data['msg']:
                    # Creating the .devicename file and store the device name
                    # public key of mysensor server
                    f = open(".devicename", 'w')
                    f.write(device + '\n')
                    if 'pubkey' in sensors:
                        pubkey = data['pubkey']
                        f.write(pubkey + '\n')
                    f.close()
                    print device + " was created at the server."
                    print "You should execute the program again."
                    print "The system halted!"
                    reactor.stop()

                elif 'msg' in sensors and 'UserCreationFailed' in data['msg']:
                    print "This user name may be already taken"
                    print "You can try it again with different username"
                    print "The system halted!"
                    reactor.stop()

def init():
    global device
    global pubkey
    global serverPubkey
    global state
    #If .device name is not there, we will read the device name from keyboard
    #else we will get it from .devicename file
    try:
        if not os.path.isfile(".devicename"):
            device = raw_input("Enter the device name: ")
            # Account need to be created at the server
            state = 'INITIAL'
        else:
            #The device name and server public key will be read form the .devicename file
            f = open(".devicename", "r")
            device = f.readline().rstrip("\n")
            serverPubkey = f.readline().rstrip("\n")
            print serverPubkey
            state = 'READY'
    except:
        print "ERRER: Cannot access the device name file."
        raise SystemExit

    #Here we will generate public and private keys for the device
    #These keys will be used to perform authentication and key exchange
    try:
        cry = myCrypto(name=device)
        #If keys are not available yet
        if not os.path.isfile(cry.pubKeyLoc):
            # Generate or loads an RSA keypair with an exponent of 65537 in PEM format
            # Private key and public key was saved in the .devicenamePriveKey and .devicenamePubKey files
            cry.generateRSA(bits=1024)
        pubkey = cry.loadRSAPubKey()
    except:
        print "ERRER: Cannot genereate private/public keys for the device."
        raise SystemExit
    print pubkey


def main():
    global host
    global port
    protocol = mySensorDatagramProtocol(host, port, reactor)
    reactor.listenUDP(0, protocol)
    reactor.run()

if __name__ == '__main__':
    init()
    main()
