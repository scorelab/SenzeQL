import PySQLPool

#CALL add_account(accno varchar(45), cid varchar(45), cname varchar(45));
#CALL add_account(%s,%s,%s)

#CALL add_agent(acode varchar(45), aname varchar(45), bcode varchar(45));
#CALL add_agent(%s,%s,%s)

#CALL `BankZ`.`add_epictr`(tr_type varchar(45), agentid int, accnum varchar(45), tamount decimal(10,2), csatus varchar(45), trF varchar(45), trT varchar(45));
#CALL add_epictr(%s,%s,%s,%s,%s,%s,%s)

#CALL add_status(tr_id int, cstatus varchar(45), sF varchar(45), sT varchar(45));
#CALL add_status(%s,%s,%s,%s)

def callproc(conn, proc, args=None):  #, proc, trtype, agentid, accno, tramount, trfrom, trto):
    query = PySQLPool.getNewQuery(conn)
    query.Query("CALL " + proc,args)