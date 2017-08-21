import PySQLPool

def callproc(conn, proc, args=None):
    query = PySQLPool.getNewQuery(conn)
    query.Query("CALL " + proc,args)