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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import edu.rpi.cmt.access.Acl.CurrentAccess;

/** Class to handle access control. Because we may be evaluating access
 * frequently we try do so without creating (many) objects.
 *
 * <p>Access is encoded to reduce the size into a number of ACEs (Access Control
 * Entries).
 *
 * <p>An ACE consists of a 'who' and a denied or allowed 'how'. We encode
 * this as a sequence of characters with the who being a length encoded as
 * digits a blank then the characters.
 *
 * <p>The 'how' part is encoded as an allow/deny character followed by the
 * encoded privilege. {@link WhoDefs}
 *
 * <p>Encoded who part is as follows:<pre>
 *  Byte 1    who/notwho   N = notWho or
 *                         W = who
 *  Byte 2    whoType,     O = owner
 *                         U = user
 *                         G = group
 *                         H = host
 *                         X = Unauthenticated
 *                         A = Authenticated
 *                         Z = Other
 *                         L = All
 *  Byte 3    String name  N = null
 *                         0 = length following
 *                         int length n
 *                         space
 *                         n characters
 *  </pre>
 *  See {@link EncodedAcl#encodeString(String)}
 *
 * Encoded how part defined in {@link PrivilegeDefs} and {@link Privileges} as a
 * blank terminated list of allowed or denied privileges.
 *
 * <p>Each member of the list is either <ul>
 * <li>'y' for allowed (was '3')</li>
 * <li>'n' for denied (was '2')</li>
 * </ul>
 *
 * <p>An inherited privilege only appears in merged acl lists created during
 * evaluation. They are not stored in the database. An inherited privilege will
 * be followed by the flag 'I' and the path of the entity which defined that
 * privilege. For example<code>'WONyAI05 /user '<code> defines:<br/>
 * who = owner, (null name) <br/>
 * Allowed All,  inherited from "/user" <br/>
 *
 * <p>This class is created for a session or perhaps a thread and reused to
 * evaluate access. For the manipulation of acls when changing them or
 * displaying allowed access, efficiency isn't such a  great concern so we
 * will normally represent the access as a  number of objects.
 *
 *  @author Mike Douglass
 */
public class Access implements Serializable {
  private boolean debug;

  /** Defines no access */
  public final static Privilege none = Privileges.makePriv(Privileges.privNone);

  /** Defines full access to an object */
  public final static Privilege all = Privileges.makePriv(Privileges.privAll);

  /** Defines read access to an object */
  public final static Privilege read = Privileges.makePriv(Privileges.privRead);

  /** Defines write access to an object */
  public final static Privilege write = Privileges.makePriv(Privileges.privWrite);

  /** Defines write access to an object */
  public final static Privilege writeContent = Privileges.makePriv(Privileges.privWriteContent);

  /** Privilege set giving any access to an object */
  public final static Privilege[] privSetAny = {};

  /** Privilege set giving read access to an object */
  public final static Privilege[] privSetRead = {read};

  /** Privilege set giving read/write access to an object */
  public final static Privilege[] privSetReadWrite = {read, write};

  /** Default access for public entities
   */
  private static volatile String defaultPublicAccess;

  /** Default access for personal entities
   */
  private static volatile String defaultPersonalAccess;

  static {
    Acl acl = new Acl();

    try {

      Collection<Privilege> allPrivs = new ArrayList<Privilege>();
      allPrivs.add(all);

      Collection<Privilege> readPrivs = new ArrayList<Privilege>();
      readPrivs.add(read);

      Collection<Privilege> noPrivs = new ArrayList<Privilege>();
      noPrivs.add(none);

      /** Public - write owner, read others, read unauthenticated */
      acl.clear();
      acl.addAce(new Ace(AceWho.owner, allPrivs, null));
      acl.addAce(new Ace(AceWho.other, readPrivs, null));
      acl.addAce(new Ace(AceWho.unauthenticated, readPrivs, null));
      defaultPublicAccess = new String(acl.encode());

      acl.clear();
      acl.addAce(new Ace(AceWho.owner, allPrivs, null));
      acl.addAce(new Ace(AceWho.other, noPrivs, null));
      defaultPersonalAccess = new String(acl.encode());
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Constructor
   *
   * @param debug    boolean true fro debug on
   * @throws AccessException
   */
  public Access(boolean debug) throws AccessException {
    this.debug = debug;
  }

  /** Get the default public access
   *
   * @return String value for default access
   */
  public static String getDefaultPublicAccess() {
    return defaultPublicAccess;
  }

  /**
   *
   * @return String default user access
   */
  public static String getDefaultPersonalAccess() {
    return defaultPersonalAccess;
  }

  /** Get a Privilege object representing the given access
   *
   * @param priv int access level
   * @return Privilege object defining access
   */
  public Privilege makePriv(int priv) {
    return Privileges.makePriv(priv);
  }

  /** Evaluating an ACL
   *
   * <p>The process of evaluating access is as follows:
   *
   * <p>For an unauthenticated (guest) user we look for an entry with an
   * unauthenticated 'who' field. If none exists access is denied othewise the
   * indicated privileges are used to determine access.
   *
   * <p>If the principal is authenticated there are a number of steps in the process
   * which are executed in the following order:
   *
   * <ol>
   * <li>If the principal is the owner then use the given access or the default.</li>
   *
   * <li>If there is a specific ACE for the user use that. </li>
   *
   * <li>Find all group entries for the given user's groups. If there is more than
   * one combine them with the more permissive taking precedence, e.g
   * write allowed overrides write denied
   * <p>If any group entries were found we're done.</li>
   *
   * <li>if there is an 'other' entry (i.e. not Owner) use that.</li>
   *
   * <li>if there is an authenticated entry use that.</li>
   *
   * <li>Otherwise apply defaults - for the owner full acccess, for any others no
   * access</li>
   *
   * @param who      Acl.Principal defining who is trying to get access
   * @param owner    owner of object
   * @param how      Privilege set definign desired access
   * @param aclString String defining current acls for object
   * @param filter    if not null specifies maximum access
   * @return CurrentAccess   access + allowed/disallowed
   * @throws AccessException
   */
  public CurrentAccess evaluateAccess(AccessPrincipal who,
                                      AccessPrincipal owner,
                                      Privilege[] how, String aclString,
                                      PrivilegeSet filter)
          throws AccessException {
    return new Acl(debug).evaluateAccess(who, owner, how,
                                         aclString.toCharArray(),
                                         filter);
  }

  /** convenience method
   *
   * @param who      Acl.Principal defining who is trying to get access
   * @param owner    owner of object
   * @param how      Privilege set defining desired access
   * @param aclChars char[] defining current acls for object
   * @param filter    if not null specifies maximum access
   * @return CurrentAccess   access + allowed/disallowed
   * @throws AccessException
   */
  public CurrentAccess evaluateAccess(AccessPrincipal who,
                                      AccessPrincipal owner,
                                      Privilege[] how, char[] aclChars,
                                      PrivilegeSet filter)
          throws AccessException {
    return new Acl(debug).evaluateAccess(who, owner, how, aclChars,
                                         filter);
  }

  /** convenience method - check for read access
   *
   * @param who      Acl.Principal defining who is trying to get access
   * @param owner    owner of object
   * @param aclChars char[] defining current acls for object
   * @param filter    if not null specifies maximum access
   * @return CurrentAccess   access + allowed/disallowed
   * @throws AccessException
   */
  public CurrentAccess checkRead(AccessPrincipal who,
                                 AccessPrincipal owner,
                                 char[] aclChars,
                                 PrivilegeSet filter)
          throws AccessException {
    return new Acl(debug).evaluateAccess(who, owner, privSetRead, aclChars,
                                         filter);
  }

  /** convenience method - check for read write access
   *
   * @param who      Acl.Principal defining who is trying to get access
   * @param owner    owner of object
   * @param aclChars char[] defining current acls for object
   * @param filter    if not null specifies maximum access
   * @return CurrentAccess   access + allowed/disallowed
   * @throws AccessException
   */
  public CurrentAccess checkReadWrite(AccessPrincipal who,
                                      AccessPrincipal owner,
                                      char[] aclChars,
                                      PrivilegeSet filter)
          throws AccessException {
    return new Acl(debug).evaluateAccess(who, owner, privSetReadWrite, aclChars,
                                         filter);
  }

  /** convenience method - check for any access
   *
   * @param who      Acl.Principal defining who is trying to get access
   * @param owner    owner of object
   * @param aclChars char[] defining current acls for object
   * @param filter    if not null specifies maximum access
   * @return CurrentAccess   access + allowed/disallowed
   * @throws AccessException
   */
  public CurrentAccess checkAny(AccessPrincipal who,
                                AccessPrincipal owner,
                                char[] aclChars,
                                PrivilegeSet filter)
          throws AccessException {
    return new Acl(debug).evaluateAccess(who, owner, privSetAny, aclChars,
                                         filter);
  }

  /** convenience method - check for given access
   *
   * @param who      Acl.Principal defining who is trying to get access
   * @param owner    owner of object
   * @param priv     int desired access as defined above
   * @param aclChars char[] defining current acls for object
   * @param filter    if not null specifies maximum access
   * @return CurrentAccess   access + allowed/disallowed
   * @throws AccessException
   */
  public CurrentAccess evaluateAccess(AccessPrincipal who,
                                      AccessPrincipal owner,
                                      int priv, char[] aclChars,
                                      PrivilegeSet filter)
          throws AccessException {
    return new Acl(debug).evaluateAccess(who, owner,
                                         new Privilege[]{Privileges.makePriv(priv)},
                                         aclChars, filter);
  }
}

