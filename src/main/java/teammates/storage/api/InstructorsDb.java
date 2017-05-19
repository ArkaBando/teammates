package teammates.storage.api;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.google.appengine.api.search.Results;
import com.google.appengine.api.search.ScoredDocument;

import teammates.common.datatransfer.InstructorSearchResultBundle;
import teammates.common.datatransfer.attributes.EntityAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Assumption;
import teammates.common.util.Const;
import teammates.common.util.Logger;
import teammates.common.util.StringHelper;
import teammates.common.util.ThreadHelper;
import teammates.storage.entity.Instructor;
import teammates.storage.search.InstructorSearchDocument;
import teammates.storage.search.InstructorSearchQuery;
import teammates.storage.search.SearchDocument;

/**
 * Handles CRUD operations for instructors.
 *
 * @see Instructor
 * @see InstructorAttributes
 */
public class InstructorsDb extends OfyEntitiesDb<Instructor, InstructorAttributes> {

    private static final Logger log = Logger.getLogger();

    /* =========================================================================
     * Methods related to Google Search API
     * =========================================================================
     */

    public void putDocument(InstructorAttributes instructorParam) {
        InstructorAttributes instructor = instructorParam;
        if (instructor.key == null) {
            instructor = this.getInstructorForEmail(instructor.courseId, instructor.email);
        }
        // defensive coding for legacy data
        if (instructor.key != null) {
            putDocument(Const.SearchIndex.INSTRUCTOR, new InstructorSearchDocument(instructor));
        }
    }

    /**
     * Batch creates or updates documents for the given instructors.
     */
    public void putDocuments(List<InstructorAttributes> instructorParams) {
        List<SearchDocument> instructorDocuments = new ArrayList<SearchDocument>();
        for (InstructorAttributes instructor : instructorParams) {
            if (instructor.key == null) {
                instructor = this.getInstructorForEmail(instructor.courseId, instructor.email);
            }
            // defensive coding for legacy data
            if (instructor.key != null) {
                instructorDocuments.add(new InstructorSearchDocument(instructor));
            }
        }
        putDocuments(Const.SearchIndex.INSTRUCTOR, instructorDocuments);
    }

    public void deleteDocument(InstructorAttributes instructorToDelete) {
        if (instructorToDelete.key == null) {
            InstructorAttributes instructor =
                    getInstructorForEmail(instructorToDelete.courseId, instructorToDelete.email);

            // handle legacy data which do not have key attribute (key == null)
            if (instructor.key != null) {
                deleteDocument(Const.SearchIndex.INSTRUCTOR, StringHelper.encrypt(instructor.key));
            }
        } else {
            deleteDocument(Const.SearchIndex.INSTRUCTOR, StringHelper.encrypt(instructorToDelete.key));
        }
    }

    /**
     * This method should be used by admin only since the searching does not restrict the
     * visibility according to the logged-in user's google ID. This is used by admin to
     * search instructors in the whole system.
     * @return null if no result found
     */

    public InstructorSearchResultBundle searchInstructorsInWholeSystem(String queryString) {

        if (queryString.trim().isEmpty()) {
            return new InstructorSearchResultBundle();
        }

        Results<ScoredDocument> results = searchDocuments(Const.SearchIndex.INSTRUCTOR,
                                                          new InstructorSearchQuery(queryString));

        return InstructorSearchDocument.fromResults(results);
    }

    /* =========================================================================
     * =========================================================================
     */

    public void createInstructors(Collection<InstructorAttributes> instructorsToAdd) throws InvalidParametersException {

        List<InstructorAttributes> instructorsToUpdate = createEntities(instructorsToAdd);

        for (InstructorAttributes instructor : instructorsToAdd) {
            if (!instructorsToUpdate.contains(instructor)) {
                putDocument(instructor);
            }
        }

        for (EntityAttributes entity : instructorsToUpdate) {
            InstructorAttributes instructor = (InstructorAttributes) entity;
            try {
                updateInstructorByEmail(instructor);
            } catch (EntityDoesNotExistException e) {
                // This situation is not tested as replicating such a situation is
                // difficult during testing
                Assumption.fail("Entity found be already existing and not existing simultaneously");
            }
            putDocument(instructor);
        }
    }

    public void createInstructorsWithoutSearchability(Collection<InstructorAttributes> instructorsToAdd)
            throws InvalidParametersException {

        List<InstructorAttributes> instructorsToUpdate = createEntities(instructorsToAdd);

        for (InstructorAttributes instructor : instructorsToUpdate) {
            try {
                updateInstructorByEmail(instructor);
            } catch (EntityDoesNotExistException e) {
                Assumption.fail("Entity found be already existing and not existing simultaneously");
            }
        }
    }

    public InstructorAttributes createInstructor(InstructorAttributes instructorToAdd)
            throws InvalidParametersException, EntityAlreadyExistsException {
        Instructor instructor = createEntity(instructorToAdd);
        if (instructor == null) {
            throw new InvalidParametersException("Created instructor is null.");
        }
        InstructorAttributes createdInstructor = new InstructorAttributes(instructor);
        putDocument(createdInstructor);
        return createdInstructor;
    }

    /**
     * Returns null if no matching objects.
     */
    public InstructorAttributes getInstructorForEmail(String courseId, String email) {

        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, email);
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, courseId);

        Instructor i = getInstructorEntityForEmail(courseId, email);

        if (i == null) {
            log.info("Trying to get non-existent Instructor: " + courseId + "/" + email);
            return null;
        }

        return new InstructorAttributes(i);
    }

    /**
     * Returns null if no matching objects.
     */
    public InstructorAttributes getInstructorForGoogleId(String courseId, String googleId) {

        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, googleId);
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, courseId);

        Instructor i = getInstructorEntityForGoogleId(courseId, googleId);

        if (i == null) {
            log.info("Trying to get non-existent Instructor: " + googleId);
            return null;
        }

        return new InstructorAttributes(i);
    }

    /**
     * Returns null if no matching instructor.
     */
    public InstructorAttributes getInstructorForRegistrationKey(String encryptedKey) {
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, encryptedKey);

        String decryptedKey;
        try {
            decryptedKey = StringHelper.decrypt(encryptedKey.trim());
        } catch (InvalidParametersException e) {
            return null;
        }

        Instructor instructor = getInstructorEntityForRegistrationKey(decryptedKey);
        if (instructor == null) {
            return null;
        }

        return new InstructorAttributes(instructor);
    }

    /**
     * Preconditions: <br>
     *  * All parameters are non-null.
     * @return empty list if no matching objects.
     */
    public List<InstructorAttributes> getInstructorsForEmail(String email) {
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, email);
        return makeAttributes(getInstructorEntitiesForEmail(email));
    }

    /**
     * Preconditions: <br>
     *  * All parameters are non-null.
     *
     * @return empty list if no matching objects.
     */
    public List<InstructorAttributes> getInstructorsForGoogleId(String googleId, boolean omitArchived) {
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, googleId);
        return makeAttributes(getInstructorEntitiesForGoogleId(googleId, omitArchived));
    }

    /**
     * Preconditions: <br>
     *  * All parameters are non-null.
     * @return empty list if no matching objects.
     */
    public List<InstructorAttributes> getInstructorsForCourse(String courseId) {
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, courseId);
        return makeAttributes(getInstructorEntitiesForCourse(courseId));
    }

    /**
     * Not scalable. Don't use unless for admin features.
     * @return {@code InstructorAttributes} objects for all instructor roles in the system
     */
    @Deprecated
    public List<InstructorAttributes> getAllInstructors() {
        return makeAttributes(getInstructorEntities());
    }

    /**
     * Updates the instructor. Cannot modify Course ID or google id.
     */
    public void updateInstructorByGoogleId(InstructorAttributes instructorAttributesToUpdate)
            throws InvalidParametersException, EntityDoesNotExistException {

        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, instructorAttributesToUpdate);

        if (!instructorAttributesToUpdate.isValid()) {
            throw new InvalidParametersException(instructorAttributesToUpdate.getInvalidityInfo());
        }
        instructorAttributesToUpdate.sanitizeForSaving();

        Instructor instructorToUpdate = getInstructorEntityForGoogleId(
                instructorAttributesToUpdate.courseId,
                instructorAttributesToUpdate.googleId);

        if (instructorToUpdate == null) {
            throw new EntityDoesNotExistException(ERROR_UPDATE_NON_EXISTENT_ACCOUNT + instructorAttributesToUpdate.googleId
                        + ThreadHelper.getCurrentThreadStack());
        }

        instructorToUpdate.setName(instructorAttributesToUpdate.name);
        instructorToUpdate.setEmail(instructorAttributesToUpdate.email);
        instructorToUpdate.setIsArchived(instructorAttributesToUpdate.isArchived);
        instructorToUpdate.setRole(instructorAttributesToUpdate.role);
        instructorToUpdate.setIsDisplayedToStudents(instructorAttributesToUpdate.isDisplayedToStudents);
        instructorToUpdate.setDisplayedName(instructorAttributesToUpdate.displayedName);
        instructorToUpdate.setInstructorPrivilegeAsText(instructorAttributesToUpdate.getTextFromInstructorPrivileges());

        //TODO: make courseId+email the non-modifiable values

        putDocument(new InstructorAttributes(instructorToUpdate));
        log.info(instructorAttributesToUpdate.getBackupIdentifier());
        ofy().save().entity(instructorToUpdate).now();
    }

    /**
     * Updates the instructor. Cannot modify Course ID or email.
     */
    public void updateInstructorByEmail(InstructorAttributes instructorAttributesToUpdate)
            throws InvalidParametersException, EntityDoesNotExistException {

        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, instructorAttributesToUpdate);

        if (!instructorAttributesToUpdate.isValid()) {
            throw new InvalidParametersException(instructorAttributesToUpdate.getInvalidityInfo());
        }
        instructorAttributesToUpdate.sanitizeForSaving();

        Instructor instructorToUpdate = getInstructorEntityForEmail(
                instructorAttributesToUpdate.courseId,
                instructorAttributesToUpdate.email);

        if (instructorToUpdate == null) {
            throw new EntityDoesNotExistException(ERROR_UPDATE_NON_EXISTENT_ACCOUNT + instructorAttributesToUpdate.email
                        + ThreadHelper.getCurrentThreadStack());
        }

        instructorToUpdate.setGoogleId(instructorAttributesToUpdate.googleId);
        instructorToUpdate.setName(instructorAttributesToUpdate.name);
        instructorToUpdate.setIsArchived(instructorAttributesToUpdate.isArchived);
        instructorToUpdate.setRole(instructorAttributesToUpdate.role);
        instructorToUpdate.setDisplayedName(instructorAttributesToUpdate.displayedName);
        instructorToUpdate.setInstructorPrivilegeAsText(instructorAttributesToUpdate.getTextFromInstructorPrivileges());

        //TODO: make courseId+email the non-modifiable values
        putDocument(new InstructorAttributes(instructorToUpdate));
        log.info(instructorAttributesToUpdate.getBackupIdentifier());
        ofy().save().entity(instructorToUpdate).now();
    }

    /**
     * Deletes the instructor specified by courseId and email.
     */
    public void deleteInstructor(String courseId, String email) {

        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, email);
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, courseId);

        Instructor instructorToDelete = getInstructorEntityForEmail(courseId, email);

        if (instructorToDelete == null) {
            return;
        }

        InstructorAttributes instructorToDeleteAttributes = new InstructorAttributes(instructorToDelete);

        deleteDocument(instructorToDeleteAttributes);
        deleteEntity(instructorToDeleteAttributes);

        Instructor instructorCheck = getInstructorEntityForEmail(courseId, email);
        if (instructorCheck != null) {
            putDocument(new InstructorAttributes(instructorCheck));
        }

        //TODO: reuse the method in the parent class instead
    }

    public void deleteInstructorsForCourses(List<String> courseIds) {
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, courseIds);
        deleteInstructors(getInstructorEntitiesForCourses(courseIds));
    }

    /**
     * Deletes all instructors with the given googleId.
     */
    public void deleteInstructorsForGoogleId(String googleId) {
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, googleId);
        deleteInstructors(getInstructorEntitiesForGoogleId(googleId));
    }

    /**
     * Deletes all instructors for the course specified by courseId.
     */
    public void deleteInstructorsForCourse(String courseId) {
        Assumption.assertNotNull(Const.StatusCodes.DBLEVEL_NULL_INPUT, courseId);
        deleteInstructors(getInstructorEntitiesForCourse(courseId));
    }

    private void deleteInstructors(List<Instructor> instructors) {
        for (Instructor instructor : instructors) {
            deleteDocument(new InstructorAttributes(instructor));
        }
        ofy().delete().entities(instructors).now();
    }

    private Instructor getInstructorEntityForGoogleId(String courseId, String googleId) {
        return ofy().load().type(Instructor.class)
                .filter("courseId =", courseId)
                .filter("googleId =", googleId)
                .first().now();
    }

    private Instructor getInstructorEntityForEmail(String courseId, String email) {
        return ofy().load().type(Instructor.class)
                .filter("courseId =", courseId)
                .filter("email =", email)
                .first().now();
    }

    private List<Instructor> getInstructorEntitiesForCourses(List<String> courseIds) {
        return ofy().load().type(Instructor.class).filter("courseId in", courseIds).list();
    }

    private Instructor getInstructorEntityForRegistrationKey(String key) {
        return ofy().load().type(Instructor.class).filter("registrationKey =", key).first().now();
    }

    private List<Instructor> getInstructorEntitiesForGoogleId(String googleId) {
        return ofy().load().type(Instructor.class).filter("googleId =", googleId).list();
    }

    /**
     * Omits instructors with isArchived == omitArchived.
     * This means that the corresponding course is archived by the instructor.
     */
    private List<Instructor> getInstructorEntitiesForGoogleId(String googleId, boolean omitArchived) {
        if (omitArchived) {
            return ofy().load().type(Instructor.class)
                    .filter("googleId =", googleId)
                    .filter("isArchived !=", true)
                    .list();
        }
        return getInstructorEntitiesForGoogleId(googleId);
    }

    private List<Instructor> getInstructorEntitiesForEmail(String email) {
        return ofy().load().type(Instructor.class).filter("email =", email).list();
    }

    private List<Instructor> getInstructorEntitiesForCourse(String courseId) {
        return ofy().load().type(Instructor.class).filter("courseId =", courseId).list();
    }

    private List<Instructor> getInstructorEntities() {
        return ofy().load().type(Instructor.class).list();
    }

    @Override
    protected Instructor getEntity(InstructorAttributes instructorToGet) {
        return getInstructorEntityForEmail(instructorToGet.courseId, instructorToGet.email);
    }

    @Override
    public boolean hasEntity(InstructorAttributes attributes) {
        return ofy().load().type(Instructor.class)
                .filter("courseId =", attributes.courseId)
                .filter("email =", attributes.email)
                .keys().first().now() != null;
    }

    private List<InstructorAttributes> makeAttributes(List<Instructor> instructors) {
        List<InstructorAttributes> instructorAttributesList = new LinkedList<InstructorAttributes>();
        for (Instructor instructor : instructors) {
            instructorAttributesList.add(new InstructorAttributes(instructor));
        }
        return instructorAttributesList;
    }

}
