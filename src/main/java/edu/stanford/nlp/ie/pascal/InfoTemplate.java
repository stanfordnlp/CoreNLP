package edu.stanford.nlp.ie.pascal;

/**
 * A partial {@link PascalTemplate}.
 * Holds URL, acronym, and name template fields.
 * 
 * @author Chris Cox
 */
public class InfoTemplate{
  String whomepage="null";
  String wacronym="null";
  String wname="null";
  String chomepage="null";
  String cacronym="null";
  String cname="null";
  
  public InfoTemplate(String whomepage, String wacronym, String wname,
                      String chomepage, String cacronym, String cname,
                        CliqueTemplates ct) {
    
    if(whomepage!=null)this.whomepage=whomepage;
    if(wacronym!=null)this.wacronym=PascalTemplate.stemAcronym(wacronym,ct);
    if(wname!=null)this.wname=wname;
    if(chomepage!=null)this.chomepage=chomepage;
    if(cacronym!=null)this.cacronym=PascalTemplate.stemAcronym(cacronym,ct);
    if(cname!=null)this.cname=cname;
  }
  
  @Override
  public int hashCode() {
    int tally=31;
    int n=7;
    tally = whomepage.hashCode()+n*wacronym.hashCode()+n*n*wname.hashCode();
    tally += (chomepage.hashCode() + 
              n*cacronym.hashCode()+ n*n*cname.hashCode());
    return tally;
  }
  
  @Override
  public boolean equals(Object obj){
    if(obj==null)return false;
    if(!( obj instanceof InfoTemplate)) return false;
    InfoTemplate i = (InfoTemplate)obj;
    
    return(whomepage.equals(i.whomepage)&&
           wacronym.equals(i.wacronym) &&
           wname.equals(i.wname) &&
           chomepage.equals(i.chomepage)&&
           cacronym.equals(i.cacronym) &&
           cname.equals(i.cname));              
  }
  
  @Override
  public String toString(){
    return ("W_URL: "+whomepage+" W_ACRO: "+wacronym+" W_NAME: "+wname+ 
            "\nC_URL: "+chomepage+" C_ACRO: "+cacronym+" C_NAME: "+cname);
  }
}
