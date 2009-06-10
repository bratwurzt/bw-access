/* **********************************************************************
    Copyright 2006 Rensselaer Polytechnic Institute. All worldwide rights reserved.

    Redistribution and use of this distribution in source and binary forms,
    with or without modification, are permitted provided that:
       The above copyright notice and this permission notice appear in all
        copies and supporting documentation;

        The name, identifiers, and trademarks of Rensselaer Polytechnic
        Institute are not used in advertising or publicity without the
        express prior written permission of Rensselaer Polytechnic Institute;

    DISCLAIMER: The software is distributed" AS IS" without any express or
    implied warranty, including but not limited to, any implied warranties
    of merchantability or fitness for a particular purpose or any warrant)'
    of non-infringement of any current or pending patent rights. The authors
    of the software make no representations about the suitability of this
    software for any particular purpose. The entire risk as to the quality
    and performance of the software is with the user. Should the software
    prove defective, the user assumes the cost of all necessary servicing,
    repair or correction. In particular, neither Rensselaer Polytechnic
    Institute, nor the authors of the software are liable for any indirect,
    special, consequential, or incidental damages related to the software,
    to the maximum extent the law permits.
*/
package edu.rpi.cmt.access;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/** Define the properties of a privilege for the calendar.
 *
 *  @author Mike Douglass   douglm @ rpi.edu
 */
/**
 * @author douglm
 *
 */
public class Privilege implements PrivilegeDefs {
  private String name;

  /** This will probably go - the description needs to come from a resource
   * and be in the appropriate language.
   */
  private String description;

  private boolean abstractPriv;

  /** Is this a denial rather than granting
   */
  private boolean denial;

  private int index;

  private char encoding;

  private ArrayList<Privilege> containedPrivileges = new ArrayList<Privilege>();

  /** Constructor
   *
   * @param name
   * @param description
   * @param abstractPriv
   * @param denial
   * @param index
   */
  public Privilege(String name,
                   String description,
                   boolean abstractPriv,
                   boolean denial,
                   int index) {
    this.name = name;
    this.description = description;
    this.abstractPriv = abstractPriv;
    this.denial = denial;
    setIndex(index);
  }

  /** Constructor for non-abstract non-denial
   *
   * @param name
   * @param description
   * @param index
   */
  public Privilege(String name,
                   String description,
                   int index) {
    this(name, description, false, false, index);
  }

  /** Constructor for non-abstract
   *
   * @param name
   * @param description
   * @param denial
   * @param index
   */
  public Privilege(String name,
                   String description,
                   boolean denial,
                   int index) {
    this(name, description, false, denial, index);
  }

  /** Constructor for non-abstract privilege container
   *
   * @param name
   * @param description
   * @param denial
   * @param index
   * @param contained
   */
  public Privilege(String name,
                   String description,
                   boolean denial,
                   int index,
                   Privilege[] contained) {
    this(name, description, false, denial, index);
    for (Privilege p: contained) {
      containedPrivileges.add(p);
    }
  }

  /**
   * @return String
   */
  public String getName() {
    return name;
  }

  /**
   * @return String
   */
  public String getDescription() {
    return description;
  }

  /**
   * @return String
   */
  public boolean getAbstractPriv() {
    return abstractPriv;
  }

  /**
   * @return String
   */
  public boolean getDenial() {
    return denial;
  }

  /**
   * @return String
   */
  public int getIndex() {
    return index;
  }

  /**
   * @return containedPrivileges
   */
  public Collection<Privilege> getContainedPrivileges() {
    return Collections.unmodifiableCollection(containedPrivileges);
  }

  /* ====================================================================
   *                 Decoding methods
   * ==================================================================== */

  /** Works its way down the tree of privileges finding the highest entry
   * that matches the privilege in the acl.
   *
   * @param allowedRoot
   * @param deniedRoot
   * @param acl
   * @return Privilege
   * @throws AccessException
   */
  public static Privilege findPriv(Privilege allowedRoot,
                                   Privilege deniedRoot,
                                   EncodedAcl acl)
          throws AccessException {
    if (acl.remaining() < 2) {
      return null;
    }

    Privilege p;

    if (matchDenied(acl)) {
      p = matchEncoding(deniedRoot, acl);
    } else {
      p = matchEncoding(allowedRoot, acl);
    }

    if (p == null) {
      acl.back();  // back up over denied flag
    }

    return p;
  }

  private static boolean matchDenied(EncodedAcl acl) throws AccessException {
    char c = acl.getChar();

    /* Expect the privilege allowed/denied flag
     * (or the oldDenied or oldAllowed flag)
     */
    if ((c == denied) || (c == oldDenied)) {
      return true;
    }

    if ((c == allowed) || (c == oldAllowed)) {
      return false;
    }

    throw AccessException.badACE("privilege flag=" + c +
                                 " " + acl.getErrorInfo());
  }

  /** We matched denied at the start. Here only the encoding is compared.
   *
   * @param subRoot Privilege
   * @param acl
   * @return Privilege or null
   * @throws AccessException
   */
  private static Privilege matchEncoding(Privilege subRoot,
                                         EncodedAcl acl) throws AccessException {
    if (acl.remaining() < 1) {
      return null;
    }

    char c = acl.getChar();

    //System.out.println("subRoot.encoding='" + subRoot.encoding + " c='" + c + "'");
    if (subRoot.encoding == c) {
      return subRoot;
    }

    /* Try the children */

    acl.back();

    for (Privilege cp: subRoot.getContainedPrivileges()) {
      Privilege p = matchEncoding(cp, acl);
      if (p != null) {
        return p;
      }
    }

    return null;
  }

  /* ====================================================================
   *                 Encoding methods
   * ==================================================================== */

  /** Encode this object as a sequence of char.
   *
   * @param acl   EncodedAcl for result.
   */
  /**
   * @param acl
   * @throws AccessException
   */
  public void encode(EncodedAcl acl) throws AccessException {
    if (denial) {
      acl.addChar(denied);
    } else {
      acl.addChar(allowed);
    }

    acl.addChar(encoding);
  }

  /** Make a copy including children with the denied flag set true
   *
   * @param val Privilege to clone
   * @return Privilege cloned value
   */
  public static Privilege cloneDenied(Privilege val) {
    Privilege newval = new Privilege(val.getName(),
                                     val.getDescription(),
                                     val.getAbstractPriv(),
                                     true,
                                     val.getIndex());

    newval.containedPrivileges.addAll(val.getContainedPrivileges());

    return newval;
  }

  /* ====================================================================
   *                    private methods
   * ==================================================================== */

  /**
   * @param val
   */
  private void setIndex(int val) {
    index = val;
    encoding = privEncoding[index];
  }

  /* ====================================================================
   *                    Object methods
   * ==================================================================== */
/*
  public int hashCode() {
    return 31 * entityId * entityType;
  }

  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null) {
      return false;
    }

    if (!(obj instanceof AttendeeVO)) {
      return false;
    }

    AttendeePK that = (AttendeePK)obj;

    return (entityId == that.entityId) &&
           (entityType == that.entityType);
  }
  */

  /** Provide a string representation for user display - this should probably
   * use a localized resource
   */
  /**
   * @return String
   */
  public String toUserString() {
    StringBuilder sb = new StringBuilder();

    if (getDenial()) {
      sb.append("NOT ");
    }

    sb.append(getName());

    return sb.toString();
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("Privilege{name=");
    sb.append(name);
    sb.append(", description=");
    sb.append(description);
    sb.append(", abstractPriv=");
    sb.append(abstractPriv);
    sb.append(", denial=");
    sb.append(denial);
    sb.append(", index=");
    sb.append(index);

    if (!containedPrivileges.isEmpty()) {
      sb.append(",\n   contains ");
      boolean first = true;
      for (Privilege p: containedPrivileges) {
        if (!first) {
          sb.append(", ");
        }
        first = false;
        sb.append(p.getName());
      }
    }
    sb.append("}");

    return sb.toString();
  }

  /** We do not clone the contained privileges - if any.
   *
   * @return Object cloned value
   */
  public Object clone() {
    return new Privilege(getName(),
                         getDescription(),
                         getAbstractPriv(),
                         getDenial(),
                         getIndex());
  }
}

