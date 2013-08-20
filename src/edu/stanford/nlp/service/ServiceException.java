package edu.stanford.nlp.service;

/**
 * Exception thrown on failure during connection to remote service.
 * 
 * @author dramage
 */
public class ServiceException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  public ServiceException(String s) {
    super(s);
  }
  public ServiceException(Exception e) {
    super(e);
  }
  public ServiceException(String s, Exception e) {
    super(s, e);
  }
}