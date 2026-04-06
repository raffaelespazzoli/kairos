package com.portal.integration.git.model;

/**
 * Represents a pull/merge request created in a Git hosting platform.
 *
 * @param url the web URL for viewing the pull request
 * @param number the platform-assigned PR/MR number
 * @param title the title of the pull request
 */
public record PullRequest(String url, int number, String title) {}
