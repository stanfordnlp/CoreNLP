package edu.stanford.nlp.ie.pascal;

/**
 * A partial {@link PascalTemplate}.  Holds date fields only.
 *
 * @author Chris Cox
 */
public class DateTemplate{

  public String subdate="1/1/1000";
  public String noadate="1/1/1000";
  public String crcdate="1/1/1000";
  public String workdate="1/1/1000";

  public DateTemplate(String subdate,String noadate,String crcdate,String workdate) {
    if(subdate!=null)this.subdate=subdate;
    if(noadate!=null)this.noadate=noadate;
    if(crcdate!=null)this.crcdate=crcdate;
    if(workdate!=null)this.workdate=workdate;
  }

  @Override
  public int hashCode() {
    int tally = 31;
    int n = 3;
    tally = tally+n*subdate.hashCode()+n*n*noadate.hashCode()+
      n*n*n*crcdate.hashCode()+n*workdate.hashCode();
    return tally;
  }

  @Override
  public boolean equals(Object obj) {
    if(obj==null)return false;
    if(! (obj instanceof DateTemplate)) return false;

    DateTemplate d = (DateTemplate)obj;
    return (subdate.equals(d.subdate) &&
            noadate.equals(d.noadate) &&
            crcdate.equals(d.crcdate) &&
            workdate.equals(d.workdate));
  }

  @Override
  public String toString() {
    return (" Sub:" + subdate + " Noa:" + noadate + " Crc:" + crcdate + " Wrk:" + workdate);
  }


}
