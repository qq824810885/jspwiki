package com.ecyrd.jspwiki.auth.login;

import java.io.IOException;
import java.security.Principal;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;

import com.ecyrd.jspwiki.WikiContext;
import com.ecyrd.jspwiki.auth.NoSuchPrincipalException;
import com.ecyrd.jspwiki.auth.WikiPrincipal;
import com.ecyrd.jspwiki.auth.authorize.Role;
import com.ecyrd.jspwiki.auth.user.UserDatabase;

/**
 * <p>
 * Logs in a user by extracting authentication data from an Http servlet
 * session. First, the module tries to extract a Principal object out of the
 * request directly using the servlet requests's <code>getUserPrincipal()</code>
 * method. If one is found, authentication succeeds. If there is no
 * Principal in the request, try calling <code>getRemoteUser()</code>. If
 * the <code>remoteUser</code> exists but the UserDatabase can't find a matching
 * profile, a generic WikiPrincipal is created with this value. If neither
 * <code>userPrincipal</code> nor <code>remoteUser</code> exist in the request, the login fails.
 * </p>
 * <p>
 * This module must be used with a CallbackHandler that supports the following
 * Callback types:
 * </p>
 * <ol>
 * <li>{@link HttpRequestCallback} - supplies the Http request object, from
 * which the getRemoteUser and getUserPrincipal are extracted</li>
 * <li>{@link UserDatabaseCallback} - supplies the user database for looking up
 * the value of getRemoteUser</li>
 * </ol>
 * <p>
 * After authentication, the Subject will contain principals
 * {@link com.ecyrd.jspwiki.auth.authorize.Role#ALL}
 * and {@link com.ecyrd.jspwiki.auth.authorize.Role#AUTHENTICATED}.
 * In addition, the Subject will contain any Principals returned by
 * {@link com.ecyrd.jspwiki.auth.user.UserDatabase#getPrincipals(String)},
 * if user profile exists, or a generic WikiPrincipal if not.</p>
 * 
 * @author Andrew Jaquith
 * @version $Revision: 1.6 $ $Date: 2005-09-24 14:25:59 $
 * @since 2.3
 */
public class WebContainerLoginModule extends AbstractLoginModule
{

    protected static Logger       log      = Logger.getLogger( WebContainerLoginModule.class );
    
    /**
     * Logs in the user.
     * @see javax.security.auth.spi.LoginModule#login()
     */
    public boolean login() throws LoginException
    {
        HttpRequestCallback requestCallback = new HttpRequestCallback();
        UserDatabaseCallback databaseCallback = new UserDatabaseCallback();
        Callback[] callbacks = new Callback[]
        { requestCallback, databaseCallback };
        String userId = null;

        try
        {
            // First, try to extract a Principal object out of the request
            // directly. If we find one, we're done.
            m_handler.handle( callbacks );
            HttpServletRequest request = requestCallback.getRequest();
            if ( request == null )
            {
                throw new LoginException( "No Http request supplied." );
            }
            HttpSession session = request.getSession(false);
            String sid = (session == null) ? NULL : session.getId(); 
            Principal principal = request.getUserPrincipal();
            if ( principal == null )
            {
                // If no Principal in request, try the remoteUser
                if ( log.isDebugEnabled() )
                {
                    log.debug( "No userPrincipal found for session ID=" + sid);
                }
                userId = request.getRemoteUser();
                if ( userId == null )
                {
                    if ( log.isDebugEnabled() )
                    {
                        log.debug( "No remoteUser found for session ID=" + sid);
                    }
                    throw new FailedLoginException( "No remote user found" );
                }
                principal = new WikiPrincipal( userId, WikiPrincipal.LOGIN_NAME );
            }
            if ( log.isDebugEnabled() )
            {
                log.debug("Added Principal " + principal.getName() + ",Role.ANONYMOUS,Role.ALL" );
            }
            m_principals.add( new PrincipalWrapper( principal ) );
            
            // Add the roles Authenticated and All
            m_principals.add( Role.AUTHENTICATED );
            m_principals.add( Role.ALL );

            // Add any user principals from the UserDatabase.
            UserDatabase database = databaseCallback.getUserDatabase();
            if ( database == null )
            {
                throw new LoginException( "User database cannot be null." );
            }
            Principal[] principals = database.getPrincipals( principal.getName() );
            for( int i = 0; i < principals.length; i++ )
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug("Added Principal " + principals[i].getName() + ",Role.ANONYMOUS,Role.ALL" );
                }
                m_principals.add( principals[i] );
            }
            return true;
        }
        catch( IOException e )
        {
            log.error( "IOException: " + e.getMessage() );
            return false;
        }
        catch( UnsupportedCallbackException e )
        {
            log.error( "UnsupportedCallbackException: " + e.getMessage() );
            return false;
        }
        catch( NoSuchPrincipalException e )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "Could not find principal in user database." );
            }
            return true;
        }
    }

}