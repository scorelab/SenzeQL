from twisted.internet.protocol import DatagramProtocol
from twisted.internet.task import LoopingCall
from twisted.internet import reactor, threads

import time
import sys
import os
import logging

sys.path.append(os.path.abspath('./'))
sys.path.append(os.path.abspath('./db'))
sys.path.append(os.path.abspath('./utils'))
sys.path.append(os.path.abspath('./models'))
sys.path.append(os.path.abspath('./handlers'))

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

if not(os.path.exists('logs')):
    os.mkdir('logs')

filehandler = logging.FileHandler('logs/client.log')
filehandler.setLevel(logging.INFO)

formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - \
                                                            %(message)s')
filehandler.setFormatter(formatter)

logger.addHandler(filehandler)


from db_handler import *
from crypto_utils import *
from senz_handler import *
from senz import *
from config import *

class SenzcProtocol(DatagramProtocol):
    """
    Protocol will connects to udp port(which server runs on). When packet(semz)
    comes to server we have to asynchornosly handle them. We are starting
    thread save twisted thread on GET, SHARE and PUT senz
    """
    def __init__(self, host, port):
        """
        initiliaze senz server host and port

        Args:
            host - server host
            port - server port
        """
        self.host = host
        self.port = port

    def startProtocol(self):
        """
        Call when twisted udp protocol starts, Few actions need to be done from
        here
            1. First need to connect to udp socket from here.
            2. Then need to share public key to server via SHARE senz
            3. Finall need to start looping call to send ping messages to
               server in every 30 mins
        """
        logger.info('client started')
        self.transport.connect(self.host, self.port)

        self.share_pubkey()
        lc = LoopingCall(self.send_ping)
        lc.start(60 * 30)

        args = [0,0,'--------',0.00,'STARTED','B','B']
        callproc(PySQLPool.getNewConnection(username='root', password='root@123', host='localhost', db='BankZ'),'add_epictr(%s,%s,%s,%s,%s,%s,%s);',args)

    def stopProtocol(self):
        """
        Call when datagram protocol stops. Need to clear global connection if
        exits from here
        """
        logger.info('client stopped')

        args = [0,0,'--------',0.00,'STOPED','B','B']
        callproc(PySQLPool.getNewConnection(username='root', password='root@123', host='localhost', db='BankZ'),'add_epictr(%s,%s,%s,%s,%s,%s,%s);',args)

    def datagramReceived(self, datagram, host):
        """
        Call when datagram recived, datagrams are senz messages in our scenario
        We have to handle receiveing senz from here. Senz handling part will be
        delegated to SenzHandler

        Args:
            datagra - senz message
            host - receving host
        """
        logger.info('datagram received %s' % datagram)

        self.handle_datagram(datagram)

    def share_pubkey(self):
        """
        Send public key of the senzy to server via SHARE senz. We have to
        digitally senz the senz before sending to server.
        SHARE senz message would be like below

            SHARE:
                #pubkey <pubkey>
                #time <time>
            @mysensors
            ^<sender> <digital signature>
        """
        pubkey = get_pubkey()
        receiver = servername
        sender = clientname

        senz = "SHARE #pubkey %s #time %s @%s ^%s" % \
                         (pubkey, time.time(), receiver, sender)
        signed_senz = sign_senz(senz)

        self.transport.write(signed_senz)

    def send_ping(self):
        """
        Send ping message to server in everty 30 minutes. The purpose of
        peroidc ping message is keeping the connection(NAT table entry).
        ping message would be like below

            DATA
                #time <time>
            @mysensors
            ^<sender> <digital signature>
        """
        receiver = 'homep'
        sender = clientname

        senz = "DATA #time %s @%s ^%s" % \
                                    (time.time(), receiver, sender)
        signed_senz = sign_senz(senz)

        self.transport.write(signed_senz)


    def handle_datagram(self, datagram):
        """
        Handle receving senz from here, we have to do
            1. Parse the datagram and obtain senz
            2. We have to ignore ping messages from server
            3. We have to handler GET, SHARE, PUT senz messages via SenzHandler
        """

        if datagram == 'PING':
            # we ingnore ping messages
            logger.info('ping received')
        else:
            # parse senz first
            senz = parse(datagram)

            # start threads for GET, PUT, DATA, SHARE senz
            handler = SenzHandler(self.transport)
            d = threads.deferToThread(handler.handleSenz, senz)
            d.addCallback(handler.postHandle)


def init():
    """
    Init client certificates from here. All keys will be stored in .keys/
    directory in project root. We have to verify thie content of that directory
    while initializing the keys
    """
    init_keys()


def start():
    """
    Start upd senz protocol from here. It means connecting to senz server. We
    have to provide server host and port details form here.(read from config)
    """

    host = serverhost
    port = serverport

    protocol = SenzcProtocol(host, port)
    reactor.listenUDP(0, protocol)
    reactor.run()


if __name__ == '__main__':
    init()
    start()
