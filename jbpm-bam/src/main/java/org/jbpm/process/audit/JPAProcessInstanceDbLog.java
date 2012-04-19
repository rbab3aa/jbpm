/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.process.audit;

import java.util.List;

import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.NotSupportedException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is essentially a very simple implementation of a service
 * that deals with ProcessInstanceLog entities. 
 * </p>
 * Please see the public methods for the interface of this service. 
 * </p>
 * Similar to the "ProcessInstanceDbLog", the idea here is that we 
 * have a entity manager factory (similar to a session factory) that
 * we repeatedly use to generate an entity manager (which is a persistence context)
 * for the specific service command. 
 * </p>
 * While ProcessInstanceLog entities do not contain LOB's (which sometimes
 * necessitate the use of tx's even in <i>read</i> situations, we use
 * transactions here none-the-less, just to be safe. Obviously, if 
 * there is already a running transaction present, we don't do anything
 * to it. 
 * </p>
 * At the end of every command, we make sure to close the entity manager
 * we've been using -- which also means that we detach any entities that
 * might be associated with the entity manager/persistence context. 
 * After all, this is a <i>service</i> which means our philosophy here 
 * is to provide a real interface, and not a leaky one. 
 */
public class JPAProcessInstanceDbLog {

    private static Logger logger = LoggerFactory.getLogger(JPAProcessInstanceDbLog.class);
    
    private static volatile Environment env;
    private static EntityManagerFactory emf;
    
    static { 
        try { 
            emf = Persistence.createEntityManagerFactory("org.jbpm.persistence.jpa");
        } catch( Exception e ) { 
             logger.debug("Unable to instantiate emf for 'org.jbpm.persistence.jpa' persistence unit: " + e.getMessage() );   
        }
    }

    @Deprecated
    public JPAProcessInstanceDbLog() {
    }
    
    @Deprecated
    public JPAProcessInstanceDbLog(Environment env){
        JPAProcessInstanceDbLog.env = env;
    }

    public static void setEnvironment(Environment newEnv) { 
        env = newEnv;
    }

    @SuppressWarnings("unchecked")
    public static List<ProcessInstanceLog> findProcessInstances() {
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        List<ProcessInstanceLog> result = em.createQuery("FROM ProcessInstanceLog").getResultList();
        closeEntityManager(em, ut);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<ProcessInstanceLog> findProcessInstances(String processId) {
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        List<ProcessInstanceLog> result = em
            .createQuery("FROM ProcessInstanceLog p WHERE p.processId = :processId")
                .setParameter("processId", processId).getResultList();
        closeEntityManager(em, ut);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<ProcessInstanceLog> findActiveProcessInstances(String processId) {
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        List<ProcessInstanceLog> result = getEntityManager()
            .createQuery("FROM ProcessInstanceLog p WHERE p.processId = :processId AND p.end is null")
                .setParameter("processId", processId).getResultList();
        closeEntityManager(em, ut);
        return result;
    }

    public static ProcessInstanceLog findProcessInstance(long processInstanceId) {
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        ProcessInstanceLog result = (ProcessInstanceLog) getEntityManager()
            .createQuery("FROM ProcessInstanceLog p WHERE p.processInstanceId = :processInstanceId")
                .setParameter("processInstanceId", processInstanceId).getSingleResult();
        closeEntityManager(em, ut);
        return result;
    }
    
    @SuppressWarnings("unchecked")
    public static List<NodeInstanceLog> findNodeInstances(long processInstanceId) {
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        List<NodeInstanceLog> result = getEntityManager()
            .createQuery("FROM NodeInstanceLog n WHERE n.processInstanceId = :processInstanceId ORDER BY date")
                .setParameter("processInstanceId", processInstanceId).getResultList();
        closeEntityManager(em, ut);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<NodeInstanceLog> findNodeInstances(long processInstanceId, String nodeId) {
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        List<NodeInstanceLog> result = getEntityManager()
            .createQuery("FROM NodeInstanceLog n WHERE n.processInstanceId = :processInstanceId AND n.nodeId = :nodeId ORDER BY date")
                .setParameter("processInstanceId", processInstanceId)
                .setParameter("nodeId", nodeId).getResultList();
        closeEntityManager(em, ut);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<VariableInstanceLog> findVariableInstances(long processInstanceId) {
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        List<VariableInstanceLog> result = getEntityManager()
            .createQuery("FROM VariableInstanceLog v WHERE v.processInstanceId = :processInstanceId ORDER BY date")
                .setParameter("processInstanceId", processInstanceId).getResultList();
        closeEntityManager(em, ut);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<VariableInstanceLog> findVariableInstances(long processInstanceId, String variableId) {
        EntityManager em = getEntityManager();
        UserTransaction ut = joinTransaction(em);
        List<VariableInstanceLog> result = em
            .createQuery("FROM VariableInstanceLog v WHERE v.processInstanceId = :processInstanceId AND v.variableId = :variableId ORDER BY date")
                .setParameter("processInstanceId", processInstanceId)
                .setParameter("variableId", variableId).getResultList();
        closeEntityManager(em, ut);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static void clear() {
            EntityManager em = getEntityManager();
            UserTransaction ut = joinTransaction(em);
            
            List<ProcessInstanceLog> processInstances = em.createQuery("FROM ProcessInstanceLog").getResultList();
            for (ProcessInstanceLog processInstance: processInstances) {
                em.remove(processInstance);
            }
            List<NodeInstanceLog> nodeInstances = em.createQuery("FROM NodeInstanceLog").getResultList();
            for (NodeInstanceLog nodeInstance: nodeInstances) {
                em.remove(nodeInstance);
            }
            List<VariableInstanceLog> variableInstances = em.createQuery("FROM VariableInstanceLog").getResultList();
            for (VariableInstanceLog variableInstance: variableInstances) {
                em.remove(variableInstance);
            }           
            closeEntityManager(em, ut);
    }

    @Deprecated
    public static void dispose() {
        if (emf != null) {
            emf.close();
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        if (emf != null) {
            emf.close();
        }
    }

    /**
     * This method opens a new transaction, if none is currently running, and joins the entity manager/persistence context
     * to that transaction. 
     * @param em The entity manager we're using. 
     * @return {@link UserTransaction} If we've started a new transaction, then we return it so that it can be closed. 
     * @throws NotSupportedException 
     * @throws SystemException 
     * @throws Exception if something goes wrong. 
     */
    private static UserTransaction joinTransaction(EntityManager em) {
        boolean newTx = false;
        UserTransaction ut = null;
        try { 
            ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
            if( ut.getStatus() == Status.STATUS_NO_TRANSACTION ) { 
                ut.begin();
                newTx = true;
            }
        } catch(Exception e) { 
            logger.error("Unable to find or open a transaction: " + e.getMessage());
            e.printStackTrace();
        }
        
        em.joinTransaction(); 
       
        if( newTx ) { 
            return ut;
        }
        return null;
    }

    /**
     * This method closes the entity manager and transaction. It also makes sure that any objects associated 
     * with the entity manager/persistence context are detached. 
     * </p>
     * Obviously, if the transaction returned by the {@link #joinTransaction(EntityManager)} method is null, 
     * nothing is done with the transaction parameter.
     * @param em The entity manager.
     * @param ut The (user) transaction.
     */
    private static void closeEntityManager(EntityManager em, UserTransaction ut) {
        em.flush(); // This saves any changes made
        em.clear(); // This makes sure that any returned entities are no longer attached to this entity manager/persistence context
        em.close(); // and this closes the entity manager
        try { 
            if( ut != null ) { 
                // There's a tx running, close it.
                ut.commit();
            }
        } catch(Exception e) { 
            logger.error("Unable to commit transaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * This method creates a entity manager. If an environment has already been 
     * provided, we use the entity manager factory present in the environment. 
     * </p> 
     * Otherwise, we assume that the persistence unit is called "org.jbpm.persistence.jpa"
     * and use that to build the entity manager factory. 
     * @return an entity manager
     */
    private static EntityManager getEntityManager() {
        EntityManager em = null;
        if (env == null) {
            em = emf.createEntityManager();
        } else {
            EntityManagerFactory emf = (EntityManagerFactory) env.get(EnvironmentName.ENTITY_MANAGER_FACTORY);
            em = emf.createEntityManager(); 
        }
        return em;
    }

}
